package id.novamusic.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Lyrics(val plain: String, val synced: List<LyricLine>)

class LyricsRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun get(title: String, artist: String): Lyrics? = withContext(Dispatchers.IO) {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "NovaMusic/0.6.0 (Android)")
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val plain = json.optString("plainLyrics").trim()
            val synced = LrcParser.parse(json.optString("syncedLyrics"))
            Lyrics(plain, synced).takeIf { it.plain.isNotBlank() || it.synced.isNotEmpty() }
        }
    }
}
