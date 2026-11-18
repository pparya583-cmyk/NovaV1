package id.novamusic.app.network

import android.content.Context
import android.net.Uri
import android.util.Base64
import id.novamusic.app.BuildConfig
import id.novamusic.app.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Spotify Web API playlist reader. Requires a valid user OAuth access token. */
class SpotifyRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("spotify_oauth", Context.MODE_PRIVATE)
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID.trim()
    private val redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI.trim()

    fun isConfigured(): Boolean = clientId.isNotBlank()

    /** Starts Spotify Authorization Code + PKCE. No client secret is embedded in the APK. */
    fun authorizationUri(): Uri {
        check(isConfigured()) { "Spotify Client ID belum diisi di local.properties." }
        val verifierBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val verifier = base64Url(verifierBytes)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val state = UUID.randomUUID().toString()
        prefs.edit().putString("pkce_verifier", verifier).putString("oauth_state", state).apply()
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
    }

    suspend fun completeAuthorization(callback: Uri) = withContext(Dispatchers.IO) {
        val returnedState = callback.getQueryParameter("state")
        val expectedState = prefs.getString("oauth_state", null)
        if (returnedState.isNullOrBlank() || returnedState != expectedState) throw IOException("Validasi OAuth Spotify gagal (state tidak cocok).")
        callback.getQueryParameter("error")?.let { throw IOException("Spotify menolak izin: $it") }
        val code = callback.getQueryParameter("code") ?: throw IOException("Kode otorisasi Spotify tidak ditemukan.")
        val verifier = prefs.getString("pkce_verifier", null) ?: throw IOException("Sesi PKCE hilang. Silakan login ulang.")
        val body = okhttp3.FormBody.Builder()
            .add("client_id", clientId).add("grant_type", "authorization_code")
            .add("code", code).add("redirect_uri", redirectUri).add("code_verifier", verifier).build()
        val request = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Spotify token exchange gagal: ${tokenErrorReason(raw, response.code)}")
            saveTokens(JSONObject(raw))
        }
        prefs.edit().remove("oauth_state").remove("pkce_verifier").apply()
    }

    suspend fun validAccessToken(): String = withContext(Dispatchers.IO) {
        val token = prefs.getString("access_token", null)
        val expiresAt = prefs.getLong("expires_at", 0L)
        if (!token.isNullOrBlank() && System.currentTimeMillis() < expiresAt - 60_000L) return@withContext token
        val refreshToken = prefs.getString("refresh_token", null) ?: throw IOException("Hubungkan akun Spotify terlebih dahulu.")
        val body = okhttp3.FormBody.Builder().add("client_id", clientId)
            .add("grant_type", "refresh_token").add("refresh_token", refreshToken).build()
        val request = Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Refresh token Spotify gagal: ${tokenErrorReason(raw, response.code)}")
            val json = JSONObject(raw)
            saveTokens(json)
            json.optString("access_token").takeIf { it.isNotBlank() } ?: throw IOException("Spotify tidak mengembalikan access token.")
        }
    }

    fun isConnected(): Boolean = !prefs.getString("refresh_token", null).isNullOrBlank() ||
        (!prefs.getString("access_token", null).isNullOrBlank() && System.currentTimeMillis() < prefs.getLong("expires_at", 0L))

    fun logout() { prefs.edit().clear().apply() }

    private fun saveTokens(json: JSONObject) {
        val editor = prefs.edit()
            .putString("access_token", json.optString("access_token"))
            .putLong("expires_at", System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { editor.putString("refresh_token", it) }
        editor.apply()
    }

    /** Error bodies from Spotify are usually JSON, but a proxy or outage may return HTML or nothing. */
    private fun tokenErrorReason(raw: String, code: Int): String =
        runCatching { JSONObject(raw).optString("error_description").takeIf { it.isNotBlank() } }
            .getOrNull() ?: "HTTP $code"

    private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    fun playlistId(input: String): String? {
        val value = input.trim()
        val fromUri = Regex("spotify:playlist:([A-Za-z0-9]+)").find(value)?.groupValues?.get(1)
        if (fromUri != null) return fromUri
        val fromUrl = Regex("open\\.spotify\\.com/playlist/([A-Za-z0-9]+)").find(value)?.groupValues?.get(1)
        return fromUrl ?: value.takeIf { it.matches(Regex("[A-Za-z0-9]{10,}")) }
    }

    suspend fun getPlaylistItems(input: String, accessToken: String): List<SongEntity> = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "Spotify belum terhubung. OAuth access token diperlukan." }
        val id = playlistId(input) ?: throw IllegalArgumentException("Link atau ID playlist Spotify tidak valid")
        val result = mutableListOf<SongEntity>()
        // A playlist can contain the same track more than once. Duplicate ids would collide as LazyColumn keys.
        val seenIds = HashSet<String>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total && result.size < 500) {
            val url = "https://api.spotify.com/v1/playlists/$id/items".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "50")
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("market", "ID")
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val reason = when (response.code) {
                        401 -> "Token Spotify kedaluwarsa atau tidak valid"
                        403 -> "Spotify hanya mengizinkan playlist milik akun yang login atau playlist kolaboratif"
                        429 -> "Batas permintaan Spotify tercapai. Coba lagi nanti"
                        else -> "Spotify API HTTP ${response.code}"
                    }
                    throw IOException(reason)
                }
                val root = JSONObject(response.body?.string() ?: throw IOException("Spotify response kosong"))
                total = root.optInt("total", 0)
                val items = root.optJSONArray("items") ?: return@withContext result
                for (i in 0 until items.length()) {
                    val wrapper = items.optJSONObject(i) ?: continue
                    val track = wrapper.optJSONObject("item") ?: wrapper.optJSONObject("track") ?: continue
                    if (track.optString("type") != "track") continue
                    val trackId = track.optString("id")
                    val title = track.optString("name")
                    if (trackId.isBlank() || title.isBlank()) continue
                    if (!seenIds.add(trackId)) continue
                    val artists = track.optJSONArray("artists")
                    val artist = (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optJSONObject(it)?.optString("name") }
                        .filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Unknown artist" }
                    val album = track.optJSONObject("album")
                    val external = track.optJSONObject("external_urls")?.optString("spotify")
                    result += SongEntity(
                        id = "spotify:$trackId", title = title, artist = artist,
                        album = album?.optString("name").orEmpty().ifBlank { "Unknown album" },
                        uri = external ?: "https://open.spotify.com/track/$trackId",
                        artwork = null, durationMs = track.optLong("duration_ms"),
                        source = "Spotify playlist", license = "Spotify metadata only; playback not provided by Spotify API",
                        playable = false, downloadable = false
                    )
                }
                offset += items.length()
                if (items.length() == 0) break
            }
        }
        result
    }
}
