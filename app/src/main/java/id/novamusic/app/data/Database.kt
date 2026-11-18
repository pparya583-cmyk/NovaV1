package id.novamusic.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String = "Unknown artist",
    val album: String = "Unknown album",
    val uri: String,
    val artwork: String? = null,
    val durationMs: Long = 0,
    val source: String = "local",
    val folder: String = "",
    val genre: String = "",
    val license: String = "",
    val downloadable: Boolean = false,
    val playable: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "artists")
data class ArtistEntity(@PrimaryKey val id: String, val name: String)
@Entity(tableName = "albums")
data class AlbumEntity(@PrimaryKey val id: String, val title: String, val artistId: String)
@Entity(tableName = "playlists")
data class PlaylistEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"], indices = [Index("songId")])
data class PlaylistSongEntity(val playlistId: Long, val songId: String, val position: Int)
@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: String, val addedAt: Long = System.currentTimeMillis())
@Entity(tableName = "play_history", indices = [Index("songId")])
data class PlayHistoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val songId: String, val playedAt: Long = System.currentTimeMillis())
@Entity(tableName = "downloads")
data class DownloadEntity(@PrimaryKey val id: Long, val songId: String, val title: String, val source: String, val license: String, val localUri: String = "")
@Entity(tableName = "search_history")
data class SearchHistoryEntity(@PrimaryKey val query: String, val searchedAt: Long = System.currentTimeMillis())

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE") fun songs(): Flow<List<SongEntity>>
    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1") suspend fun song(id: String): SongEntity?
    @Query("SELECT * FROM songs WHERE id IN (:ids)") suspend fun songsByIds(ids: List<String>): List<SongEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSongs(songs: List<SongEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSong(song: SongEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertArtists(artists: List<ArtistEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAlbums(albums: List<AlbumEntity>)
    @Query("SELECT id FROM songs WHERE source = 'local'") suspend fun localSongIds(): List<String>
    @Query("DELETE FROM songs WHERE id IN (:ids) AND source = 'local'") suspend fun deleteLocalSongs(ids: List<String>)
    @Query("DELETE FROM songs WHERE source = 'local'") suspend fun clearLocal()
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") fun playlists(): Flow<List<PlaylistEntity>>
    @Insert suspend fun addPlaylist(playlist: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun renamePlaylist(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :id") suspend fun clearPlaylist(id: Long)
    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :id ORDER BY ps.position ASC") fun playlistSongs(id: Long): Flow<List<SongEntity>>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :id") suspend fun nextPlaylistPosition(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addToPlaylist(link: PlaylistSongEntity): Long
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId") suspend fun removeFromPlaylist(playlistId: Long, songId: String)
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC") fun favorites(): Flow<List<FavoriteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun favorite(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE songId = :id") suspend fun unfavorite(id: String)
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :id)") suspend fun isFavorite(id: String): Boolean
    @Insert suspend fun addHistory(history: PlayHistoryEntity)
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 100") fun history(): Flow<List<PlayHistoryEntity>>
    @Query("SELECT songId, COUNT(*) AS plays FROM play_history GROUP BY songId ORDER BY plays DESC LIMIT 50") fun mostPlayed(): Flow<List<PlayCount>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSearch(search: SearchHistoryEntity)
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 12") fun searches(): Flow<List<SearchHistoryEntity>>
    @Query("DELETE FROM search_history") suspend fun clearSearches()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addDownload(download: DownloadEntity)
    @Query("SELECT * FROM downloads ORDER BY id DESC") fun downloads(): Flow<List<DownloadEntity>>
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun removeDownload(id: Long)
}

data class PlayCount(val songId: String, val plays: Int)

@Database(entities = [SongEntity::class, ArtistEntity::class, AlbumEntity::class, PlaylistEntity::class,
    PlaylistSongEntity::class, FavoriteEntity::class, PlayHistoryEntity::class,
    DownloadEntity::class, SearchHistoryEntity::class], version = 1, exportSchema = false)
abstract class NovaDatabase : RoomDatabase() { abstract fun musicDao(): MusicDao }
