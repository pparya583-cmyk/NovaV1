package id.novamusic.app.network

import android.net.Uri
import id.novamusic.app.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

class ArchiveRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val itemCache = ConcurrentHashMap<String, SongEntity>()
    private val queryCache = ConcurrentHashMap<String, Pair<Long, List<SongEntity>>>()
    private suspend fun json(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "NovaMusic/0.8.0 (Android metadata client)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")
            JSONObject(response.body?.string() ?: throw IOException("Empty response"))
        }
    }

    suspend fun search(term: String): List<SongEntity> = coroutineScope {
        val normalized = ArchiveSearchPolicy.normalize(term)
        if (normalized.isBlank()) return@coroutineScope emptyList()
        val cacheKey = normalized.lowercase()
        val cached = queryCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < 15 * 60_000) return@coroutineScope cached.second
        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", "mediatype:audio AND ($normalized)")
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("rows", "8")
            .addQueryParameter("output", "json").build().toString()
        val docs = json(url).getJSONObject("response").getJSONArray("docs")
        val results = (0 until docs.length()).map { i ->
            async { runCatching { item(docs.getJSONObject(i).getString("identifier")) }.getOrNull() }
        }.awaitAll().filterNotNull()
        if (queryCache.size >= 32) queryCache.clear()
        queryCache[cacheKey] = System.currentTimeMillis() to results
        results
    }

    private suspend fun item(identifier: String): SongEntity? {
        itemCache[identifier]?.let { return it }
        val root = json("https://archive.org/metadata/${Uri.encode(identifier)}")
        val metadata = root.optJSONObject("metadata") ?: return null
        val files = root.optJSONArray("files") ?: return null
        // Do not promote free-text rights labels into streaming permission.
        val license = metadata.optString("licenseurl").trim()
        // A permissive-looking label is only a screening signal, never a legal guarantee.
        val authorized = RightsPolicy.mayStream(license)
        val downloadable = authorized && RightsPolicy.mayDownload(license)
        val file = (0 until files.length()).map { files.getJSONObject(it) }.firstOrNull { f ->
            val name = f.optString("name").lowercase()
            val formatOk = listOf(".mp3", ".ogg", ".flac", ".m4a", ".wav", ".opus", ".aac").any { name.endsWith(it) }
            val privateFile = f.optString("private").equals("true", ignoreCase = true)
            val directory = f.optString("source").equals("metadata", ignoreCase = true)
            val size = f.optString("size").toLongOrNull() ?: 0L
            val encrypted = f.optString("encryption").isNotBlank()
            formatOk && !privateFile && !directory && !encrypted && size > 0L
        } ?: return null
        val name = file.optString("name")
        val audioUrl = "https://archive.org/download/${Uri.encode(identifier)}/${name.split('/').joinToString("/") { Uri.encode(it) }}"
        val song = SongEntity(
            id = "archive:$identifier:$name", title = metadata.optString("title").ifBlank { identifier },
            artist = metadata.optString("creator").ifBlank { "Internet Archive" },
            album = metadata.optString("title").ifBlank { identifier }, uri = audioUrl,
            artwork = "https://archive.org/services/img/${Uri.encode(identifier)}",
            source = "Internet Archive", license = license.ifBlank { "Unspecified rights" },
            downloadable = authorized && downloadable, playable = authorized
        )
        if (itemCache.size >= 256) itemCache.clear()
        itemCache[identifier] = song
        return song
    }
}
