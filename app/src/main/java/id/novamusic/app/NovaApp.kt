package id.novamusic.app

import android.app.Application
import androidx.room.Room
import id.novamusic.app.data.*
import id.novamusic.app.network.ArchiveRepository
import id.novamusic.app.network.LyricsRepository

class NovaApp : Application() {
    val database by lazy { Room.databaseBuilder(this, NovaDatabase::class.java, "nova.db").build() }
    val dao by lazy { database.musicDao() }
    val prefs by lazy { NovaPreferences(this) }
    val local by lazy { LocalMusicRepository(this, dao) }
    val archive by lazy { ArchiveRepository() }
    val lyrics by lazy { LyricsRepository() }
}
