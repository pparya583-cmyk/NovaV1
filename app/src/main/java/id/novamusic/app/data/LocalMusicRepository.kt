package id.novamusic.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicRepository(private val context: Context, private val dao: MusicDao) {
    fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext 0
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID
        )
        if (Build.VERSION.SDK_INT >= 29) projection += MediaStore.Audio.Media.RELATIVE_PATH
        val found = mutableListOf<SongEntity>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            fun str(name: String): String = cursor.getString(cursor.getColumnIndexOrThrow(name)) ?: ""
            fun long(name: String): Long = cursor.getLong(cursor.getColumnIndexOrThrow(name))
            while (cursor.moveToNext()) {
                val id = long(MediaStore.Audio.Media._ID)
                val albumId = long(MediaStore.Audio.Media.ALBUM_ID)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val folder = if (Build.VERSION.SDK_INT >= 29) str(MediaStore.Audio.Media.RELATIVE_PATH).trimEnd('/') else ""
                found += SongEntity(
                    id = "local:$id", title = str(MediaStore.Audio.Media.TITLE).ifBlank { "Untitled" },
                    artist = str(MediaStore.Audio.Media.ARTIST).ifBlank { "Unknown artist" },
                    album = str(MediaStore.Audio.Media.ALBUM).ifBlank { "Unknown album" },
                    uri = uri.toString(),
                    artwork = "content://media/external/audio/albumart/$albumId",
                    durationMs = long(MediaStore.Audio.Media.DURATION), folder = folder,
                    addedAt = long(MediaStore.Audio.Media.DATE_ADDED) * 1000
                )
            }
        }
        val artists = found.map { ArtistEntity("artist:${it.artist.lowercase()}", it.artist) }.distinctBy { it.id }
        val albums = found.map { AlbumEntity("album:${it.artist.lowercase()}:${it.album.lowercase()}", it.album,
            "artist:${it.artist.lowercase()}") }.distinctBy { it.id }
        dao.upsertArtists(artists)
        dao.upsertAlbums(albums)
        dao.upsertSongs(found)
        val foundIds = found.mapTo(HashSet()) { it.id }
        val staleIds = dao.localSongIds().filterNot { it in foundIds }
        // Stay below SQLite's bind-variable limit even for libraries with thousands of tracks.
        staleIds.chunked(500).forEach { batch -> dao.deleteLocalSongs(batch) }
        found.size
    }
}
