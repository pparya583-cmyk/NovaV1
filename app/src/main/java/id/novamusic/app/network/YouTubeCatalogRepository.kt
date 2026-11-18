package id.novamusic.app.network

import id.novamusic.app.BuildConfig
import id.novamusic.app.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Official YouTube Data API search. Returns catalog metadata only, never an extracted audio stream. */
class YouTubeCatalogRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): List<SongEntity> = withContext(Dispatchers.IO) {
        val key = BuildConfig.YOUTUBE_API_KEY
        if (key.isBlank()) throw IllegalStateException("YOUTUBE_API_KEY belum diatur di local.properties")
        val url = "https://www.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("type", "video")
            .addQueryParameter("videoCategoryId", "10")
            .addQueryParameter("maxResults", "20")
            .addQueryParameter("regionCode", "ID")
            .addQueryParameter("q", query.take(120))
            .addQueryParameter("key", key)
            .build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube API HTTP ${response.code}")
            val root = JSONObject(response.body?.string() ?: throw IOException("YouTube API response kosong"))
            val items = root.optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                    val snippet = item.optJSONObject("snippet") ?: continue
                    if (id.isBlank()) continue
                    val thumbnails = snippet.optJSONObject("thumbnails")
                    val artwork = thumbnails?.optJSONObject("high")?.optString("url")
                        ?: thumbnails?.optJSONObject("medium")?.optString("url")
                    add(SongEntity(
                        id = "youtube:$id",
                        title = snippet.optString("title").replace(Regex("<[^>]*>"), ""),
                        artist = snippet.optString("channelTitle").ifBlank { "YouTube creator" },
                        album = "YouTube catalog",
                        uri = "https://www.youtube.com/watch?v=$id",
                        artwork = artwork,
                        source = "YouTube catalog",
                        license = "Metadata only; playback opens YouTube",
                        playable = false,
                        downloadable = false
                    ))
                }
            }
        }
    }
}
