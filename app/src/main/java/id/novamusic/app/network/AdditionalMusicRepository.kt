package id.novamusic.app.network

import android.net.Uri
import android.util.Xml
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
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Additional no-key, full-file music catalogues. Every adapter rejects preview-only/unsupported items. */
class AdditionalMusicRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private suspend fun body(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("User-Agent", "NovaMusic/1.0.8 (Android music catalogue client)")
            .header("Accept", "application/json, application/rss+xml, application/xml, text/html, */*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")
            response.body?.string() ?: throw IOException("Empty catalogue response")
        }
    }

    suspend fun searchAll(query: String): List<SongEntity> = coroutineScope {
        val q = query.trim().take(100)
        if (q.isBlank()) return@coroutineScope emptyList()
        listOf(
            async { runCatching { searchOpenverse(q, null) }.getOrDefault(emptyList()) },
            async { runCatching { searchOpenverse(q, "jamendo") }.getOrDefault(emptyList()) },
            async { runCatching { searchWikimedia(q) }.getOrDefault(emptyList()) },
            async { runCatching { searchCcMixter(q) }.getOrDefault(emptyList()) },
            async { runCatching { searchScottBuckley(q) }.getOrDefault(emptyList()) }
        ).awaitAll().flatten().distinctBy { it.id }
    }

    /** Openverse is anonymous-accessible. Direct audio URLs are used, not preview players. */
    private suspend fun searchOpenverse(query: String, onlySource: String?): List<SongEntity> {
        val builder = "https://api.openverse.org/v1/audio/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("category", "music")
            .addQueryParameter("page_size", "20")
            .addQueryParameter("filter_dead", "true")
        if (onlySource != null) builder.addQueryParameter("source", onlySource)
        val root = JSONObject(body(builder.build().toString()))
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val item = results.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("url")
            val licenseUrl = item.optString("license_url")
            val duration = item.optLong("duration", 0L)
            if (!url.startsWith("https://") || !isAudioUrl(url) || duration < 90L || !RightsPolicy.mayStream(licenseUrl)) return@mapNotNull null
            val provider = item.optString("provider").ifBlank { item.optString("source") }
            val source = if (provider.equals("jamendo", true) || onlySource == "jamendo") "Jamendo (via Openverse)" else "Openverse · $provider"
            SongEntity(
                id = "openverse:${item.optString("id")}",
                title = item.optString("title").ifBlank { "Untitled audio" },
                artist = item.optString("creator").ifBlank { "Unknown artist" },
                album = item.optString("audio_set").ifBlank { source },
                uri = url,
                artwork = item.optString("thumbnail").takeIf { it.startsWith("https://") },
                durationMs = duration * 1000L,
                source = source,
                license = "${item.optString("license")} ${item.optString("license_version")} · ${licenseUrl}",
                downloadable = false,
                playable = true
            )
        }
    }

    /** Wikimedia Commons file API returns the original file URL and per-file licence metadata. */
    private suspend fun searchWikimedia(query: String): List<SongEntity> {
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", "filemime:audio $query")
            .addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrlimit", "20")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|size|extmetadata")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build().toString()
        val pages = JSONObject(body(url)).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        return (0 until pages.length()).mapNotNull { i ->
            val page = pages.optJSONObject(i) ?: return@mapNotNull null
            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            val audioUrl = info.optString("url")
            if (!audioUrl.startsWith("https://") || !isAudioUrl(audioUrl) || info.optLong("size", 0L) < 1_000_000L) return@mapNotNull null
            val ext = info.optJSONObject("extmetadata") ?: return@mapNotNull null
            val licenseUrl = ext.optJSONObject("LicenseUrl")?.optString("value").orEmpty()
            val licenseName = ext.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
            if (!RightsPolicy.mayStream(licenseUrl)) return@mapNotNull null
            val title = page.optString("title").removePrefix("File:").substringBeforeLast('.')
            val description = ext.optJSONObject("ImageDescription")?.optString("value").orEmpty()
            SongEntity(
                id = "commons:${page.optLong("pageid")}", title = title,
                artist = ext.optJSONObject("Artist")?.optString("value")?.replace(Regex("<[^>]*>"), "")?.ifBlank { "Wikimedia contributor" } ?: "Wikimedia contributor",
                album = "Wikimedia Commons", uri = audioUrl,
                artwork = null, durationMs = 0L, source = "Wikimedia Commons",
                genre = "", license = "$licenseName · $licenseUrl ${description.take(180)}",
                downloadable = false, playable = true
            )
        }
    }

    /** ccMixter's public Query API exposes RSS enclosures containing the complete uploaded audio. */
    private suspend fun searchCcMixter(query: String): List<SongEntity> {
        val url = "https://ccmixter.org/api/query".toHttpUrl().newBuilder()
            .addQueryParameter("f", "rss")
            .addQueryParameter("t", "search_uploads")
            .addQueryParameter("limit", "20")
            .addQueryParameter("search_type", "any")
            .addQueryParameter("s", query)
            .addQueryParameter("lic", "by")
            .build().toString()
        val xml = body(url)
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val songs = mutableListOf<SongEntity>()
        var inItem = false
        var title = ""
        var creator = "ccMixter artist"
        var enclosure = ""
        var enclosureLength = 0L
        var license = "https://creativecommons.org/licenses/by/4.0/"
        var duration = 0L
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "item" -> { inItem = true; title = ""; creator = "ccMixter artist"; enclosure = ""; enclosureLength = 0L; duration = 0L }
                    "title" -> if (inItem) title = parser.nextText()
                    "creator" -> if (inItem) creator = parser.nextText()
                    "enclosure" -> if (inItem) { enclosure = parser.getAttributeValue(null, "url").orEmpty(); enclosureLength = parser.getAttributeValue(null, "length")?.toLongOrNull() ?: 0L }
                    "license" -> if (inItem) license = parser.getAttributeValue(null, "href") ?: parser.nextText()
                    "duration" -> if (inItem) duration = parser.nextText().toLongOrNull() ?: 0L
                }
                XmlPullParser.END_TAG -> if (parser.name.equals("item", true)) {
                    if (enclosure.startsWith("https://") && isAudioUrl(enclosure) && (duration >= 90L || (duration == 0L && enclosureLength >= 1_000_000L))) {
                        songs += SongEntity(
                            id = "ccmixter:${enclosure.hashCode()}", title = title.ifBlank { "Untitled" },
                            artist = creator, album = "ccMixter", uri = enclosure,
                            artwork = null, durationMs = duration * 1000L, source = "ccMixter",
                            license = "Creative Commons Attribution · $license", downloadable = false, playable = true
                        )
                    }
                    inItem = false
                }
            }
            event = parser.next()
        }
        return songs
    }

    /** Scott Buckley's library pages explicitly link to MP3 (Full Mix), not teaser clips. */
    private suspend fun searchScottBuckley(query: String): List<SongEntity> {
        val searchUrl = "https://www.scottbuckley.com.au/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query).build().toString()
        val html = body(searchUrl)
        val pageUrls = Regex("https://www\\.scottbuckley\\.com\\.au/library/[a-z0-9-]+/?", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }.distinct().take(8).toList()
        return pageUrls.mapNotNull { pageUrl ->
            runCatching {
                val page = body(pageUrl)
                val audio = Regex("https://www\\.scottbuckley\\.com\\.au/library/wp-content/uploads/[^\\\"'<> ]+\\.mp3", RegexOption.IGNORE_CASE)
                    .find(page)?.value ?: return@runCatching null
                val title = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(page)?.groupValues?.getOrNull(1)?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotBlank() }
                    ?: pageUrl.substringAfterLast('/').trim('/')
                SongEntity(
                    id = "scottbuckley:${audio.hashCode()}", title = title,
                    artist = "Scott Buckley", album = "Scott Buckley Music Library", uri = audio,
                    artwork = null, source = "Scott Buckley",
                    license = "CC BY 4.0 · credit Scott Buckley · https://creativecommons.org/licenses/by/4.0/",
                    downloadable = false, playable = true
                )
            }.getOrNull()
        }
    }

    private fun isAudioUrl(value: String): Boolean {
        val path = runCatching { Uri.parse(value).lastPathSegment.orEmpty().lowercase() }.getOrDefault("")
        return listOf(".mp3", ".ogg", ".oga", ".flac", ".m4a", ".wav", ".opus", ".aac").any { path.endsWith(it) }
    }
}
