package id.novamusic.app.network

import id.novamusic.app.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Audius public read/search + artist-authorized stream URLs. No API key is required for read-only calls. */
class AudiusRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val cache = ConcurrentHashMap<String, Pair<Long, List<SongEntity>>>()

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NovaMusic/1.0.8 (Android music discovery)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Audius HTTP ${response.code}: ${response.message}")
            JSONObject(response.body?.string() ?: throw IOException("Audius returned an empty response"))
        }
    }

    suspend fun search(term: String): List<SongEntity> {
        val query = term.trim().take(100)
        if (query.isBlank()) return emptyList()
        val key = query.lowercase()
        cache[key]?.let { if (System.currentTimeMillis() - it.first < 5 * 60_000) return it.second }

        val url = "https://api.audius.co/v1/tracks/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("app_name", "NovaMusic")
            .addQueryParameter("limit", "20")
            .build().toString()
        val root = getJson(url)
        val data = root.optJSONArray("data") ?: return emptyList()
        val results = (0 until data.length()).mapNotNull { index ->
            val track = data.optJSONObject(index) ?: return@mapNotNull null
            val id = track.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Audius explicitly marks whether an artist-uploaded track is streamable.
            if (!track.optBoolean("is_streamable", false) || track.optLong("duration", 0L) < 90L) return@mapNotNull null
            val title = track.optString("title").ifBlank { "Untitled" }
            val user = track.optJSONObject("user")
            val artist = user?.optString("name").orEmpty().ifBlank { "Audius artist" }
            val artwork = track.optJSONObject("artwork")?.let { art ->
                art.optString("150x150").ifBlank { art.optString("480x480") }
            }?.takeIf { it.startsWith("https://") }
            val stream = "https://api.audius.co/v1/tracks/${id}/stream?app_name=NovaMusic"
            SongEntity(
                id = "audius:$id",
                title = title,
                artist = artist,
                album = track.optString("album").ifBlank { "Audius" },
                uri = stream,
                artwork = artwork,
                durationMs = track.optLong("duration", 0L).coerceAtLeast(0L) * 1000L,
                source = "Audius",
                genre = track.optString("genre"),
                license = "Audius streamable track; see artist track page for terms",
                downloadable = false,
                playable = true
            )
        }
        if (cache.size > 40) cache.clear()
        cache[key] = System.currentTimeMillis() to results
        return results
    }
}
