package id.novamusic.app

import android.app.Application
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import id.novamusic.app.data.*
import id.novamusic.app.network.Lyrics
import id.novamusic.app.player.MusicService
import id.novamusic.app.player.QueueIndexPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NovaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NovaApp
    private val dao = app.dao
    private val prefs = app.prefs
    private var controller: MediaController? = null
    private val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync()
    private var sleepJob: Job? = null
    private var scanJob: Job? = null
    private var searchJob: Job? = null
    private var lyricsJob: Job? = null
    private var volumePersistJob: Job? = null
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scanJob?.cancel()
            scanJob = viewModelScope.launch { delay(800); if (app.local.hasPermission()) app.local.scan() }
        }
    }

    val songs = dao.songs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = dao.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val searches = dao.searches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloads = dao.downloads().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sort = prefs.sort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Title")
    val reduceMotion = prefs.reduceMotion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val theme = prefs.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Daylight Glass")
    val uiStyle = prefs.uiStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Glass")

    val screen = MutableStateFlow("Home")
    val selectedPlaylist = MutableStateFlow<Long?>(null)
    val online = MutableStateFlow<List<SongEntity>>(emptyList())
    val searching = MutableStateFlow(false)
    val spotifyConnected = MutableStateFlow(app.spotify.isConnected())
    val error = MutableStateFlow<String?>(null)
    val ready = MutableStateFlow(false)
    val currentId = MutableStateFlow<String?>(null)
    val playing = MutableStateFlow(false)
    val progress = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val queue = MutableStateFlow<List<String>>(emptyList())
    val currentIndex = MutableStateFlow(-1)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val speed = MutableStateFlow(1f)
    val volume = MutableStateFlow(1f)
    val lyrics = MutableStateFlow<Lyrics?>(null)
    val loadingLyrics = MutableStateFlow(false)
    val sleepMinutes = MutableStateFlow(0)
    val downloadStatus = MutableStateFlow<Map<Long, Pair<Int, Int>>>(emptyMap())

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncPlayer()
            // Persist on player state changes as well as the periodic checkpoint.
            viewModelScope.launch { persistQueue() }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncPlayer()
            mediaItem?.mediaId?.let { id ->
                viewModelScope.launch { dao.addHistory(PlayHistoryEntity(songId = id)) }
                loadLyrics(id)
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            this@NovaViewModel.error.value = "Audio gagal diputar: ${error.errorCodeName}"
        }
    }

    init {
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        future.addListener({
            runCatching {
                controller = future.get()
                controller?.addListener(listener)
                ready.value = true
                viewModelScope.launch {
                    // Apply persisted audio preferences even when no queue was saved.
                    controller?.volume = prefs.volume.first()
                    controller?.setPlaybackSpeed(prefs.speed.first().coerceIn(0.25f, 2f))
                    controller?.shuffleModeEnabled = prefs.shuffle.first()
                    controller?.repeatMode = prefs.repeat.first().coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
                    restoreQueue()
                    syncPlayer()
                }
            }.onFailure { error.value = "Media controller: ${it.localizedMessage}" }
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                controller?.let { p ->
                    progress.value = p.currentPosition.coerceAtLeast(0)
                    duration.value = p.duration.coerceAtLeast(0)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (controller != null) persistQueue()
            }
        }
        viewModelScope.launch { if (app.local.hasPermission()) refreshLibrary() }
        viewModelScope.launch {
            val deadline = prefs.sleepDeadline.first()
            if (deadline > 0L) scheduleSleepDeadline(deadline)
        }
    }

    fun navigate(to: String) { screen.value = to }
    fun message(text: String) { error.value = text }
    fun dismissError() { error.value = null }

    fun refreshLibrary() = viewModelScope.launch {
        if (!app.local.hasPermission()) { message("Izin audio belum diberikan. Aktifkan lewat Settings Android."); return@launch }
        runCatching { app.local.scan() }
            .onSuccess { message("Library diperbarui: $it lagu lokal") }
            .onFailure { message("Gagal memindai musik: ${it.localizedMessage}") }
    }

    fun searchOnline(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            online.value = emptyList()
            searching.value = false
            return
        }
        searching.value = true
        searchJob = viewModelScope.launch {
            delay(300) // Debounce rapid typing to avoid unnecessary network requests.
            try {
                dao.addSearch(SearchHistoryEntity(query.trim()))
                online.value = app.youtube.search(query.trim())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                online.value = emptyList()
                message("Pencarian online gagal: ${failure.localizedMessage ?: "Periksa koneksi internet."}")
            } finally {
                if (currentCoroutineContext().isActive) searching.value = false
            }
        }
    }

    fun connectSpotify() {
        try {
            val auth = app.spotify.authorizationUri()
            app.startActivity(Intent(Intent.ACTION_VIEW, auth).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (failure: Exception) {
            message(failure.localizedMessage ?: "Spotify Client ID belum dikonfigurasi.")
        }
    }

    fun handleSpotifyCallback(uri: Uri) = viewModelScope.launch {
        if (uri.scheme != "novamusic" || uri.host != "spotify-callback") return@launch
        searching.value = true
        try {
            app.spotify.completeAuthorization(uri)
            spotifyConnected.value = app.spotify.isConnected()
            message("Spotify berhasil terhubung. Playlist siap diimpor.")
        } catch (failure: Exception) {
            message("Login Spotify gagal: ${failure.localizedMessage ?: "Silakan coba lagi."}")
        } finally { searching.value = false }
    }

    fun disconnectSpotify() {
        app.spotify.logout()
        spotifyConnected.value = false
        message("Akun Spotify diputus.")
    }

    fun importSpotifyPlaylist(url: String) = viewModelScope.launch {
        searching.value = true
        try {
            val token = app.spotify.validAccessToken()
            val imported = app.spotify.getPlaylistItems(url, token)
            online.value = imported
            message("Playlist Spotify dimuat: ${imported.size} track metadata")
        } catch (failure: Exception) {
            message("Impor Spotify gagal: ${failure.localizedMessage ?: "Periksa koneksi dan akses playlist."}")
        } finally { searching.value = false }
    }

    fun clearSearchHistory() = viewModelScope.launch { dao.clearSearches() }

    private fun media(song: SongEntity): MediaItem {
        val meta = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album)
        song.artwork?.let { meta.setArtworkUri(Uri.parse(it)) }
        return MediaItem.Builder().setMediaId(song.id).setUri(song.uri).setMediaMetadata(meta.build()).build()
    }

    fun playSong(song: SongEntity, from: List<SongEntity> = listOf(song)) = viewModelScope.launch {
        if (!song.playable) { message("Sumber ini tidak memiliki lisensi yang dapat diverifikasi untuk diputar."); return@launch }
        val p = controller ?: run { message("Player sedang tersambung, coba lagi."); return@launch }
        val allowed = from.filter { it.playable }.ifEmpty { listOf(song) }
        dao.upsertSongs(allowed)
        p.setMediaItems(allowed.map(::media), allowed.indexOfFirst { it.id == song.id }.coerceAtLeast(0), 0)
        p.prepare()
        p.play()
        syncPlayer()
        persistQueue()
    }

    fun playAll(items: List<SongEntity>, randomized: Boolean = false) {
        val list = items.filter { it.playable }.let { if (randomized) it.shuffled() else it }
        if (list.isNotEmpty()) playSong(list.first(), list) else message("Tidak ada lagu yang dapat diputar")
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() }; syncPlayer() }
    fun next() { controller?.seekToNextMediaItem(); syncPlayer() }
    fun previous() { controller?.seekToPreviousMediaItem(); syncPlayer() }
    fun seek(ms: Long) { controller?.seekTo(ms); syncPlayer() }
    fun toggleShuffle() {
        controller?.let { player ->
            val enabled = !player.shuffleModeEnabled
            player.shuffleModeEnabled = enabled
            syncPlayer()
            viewModelScope.launch { prefs.setShuffle(enabled); persistQueue() }
        }
    }
    fun cycleRepeat() {
        controller?.let { it.repeatMode = (it.repeatMode + 1) % 3; syncPlayer() }
        viewModelScope.launch { prefs.setRepeat(repeat.value) }
    }
    fun setSpeed(value: Float) {
        controller?.setPlaybackSpeed(value); speed.value = value
        viewModelScope.launch { prefs.setSpeed(value) }
    }
    fun setVolume(value: Float) {
        val safe = value.coerceIn(0f, 1f)
        controller?.volume = safe
        volume.value = safe
        volumePersistJob?.cancel()
        volumePersistJob = viewModelScope.launch {
            delay(250) // Avoid a DataStore write for every slider frame.
            prefs.setVolume(safe)
        }
    }

    fun addToQueue(song: SongEntity, playNext: Boolean = false) = viewModelScope.launch {
        if (!song.playable) { message("Lagu ini tidak memiliki izin streaming yang terverifikasi"); return@launch }
        val p = controller ?: return@launch
        dao.upsertSong(song)
        if (playNext) p.addMediaItem(QueueIndexPolicy.playNextInsertionIndex(p.currentMediaItemIndex, p.mediaItemCount), media(song))
        else p.addMediaItem(media(song))
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        syncPlayer(); persistQueue()
    }

    fun removeQueue(index: Int) = viewModelScope.launch {
        controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
        syncPlayer(); persistQueue()
    }
    fun moveQueue(from: Int, to: Int) = viewModelScope.launch {
        controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) }
        syncPlayer(); persistQueue()
    }
    fun clearQueue() = viewModelScope.launch {
        controller?.stop(); controller?.clearMediaItems()
        syncPlayer(); prefs.setQueue("")
    }

    private fun syncPlayer() {
        controller?.let { p ->
            currentId.value = p.currentMediaItem?.mediaId
            playing.value = p.isPlaying
            queue.value = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
            currentIndex.value = p.currentMediaItemIndex
            progress.value = p.currentPosition.coerceAtLeast(0)
            duration.value = p.duration.coerceAtLeast(0)
            shuffle.value = p.shuffleModeEnabled
            repeat.value = p.repeatMode
            speed.value = p.playbackParameters.speed
            volume.value = p.volume
        }
    }

    private suspend fun persistQueue() {
        val p = controller ?: return
        val json = JSONObject()
            .put("ids", JSONArray((0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }))
            .put("index", p.currentMediaItemIndex)
            .put("position", p.currentPosition.coerceAtLeast(0))
            .put("shuffle", p.shuffleModeEnabled)
            .put("repeat", p.repeatMode)
            .put("speed", p.playbackParameters.speed)
        prefs.setQueue(json.toString())
    }

    private suspend fun restoreQueue() {
        val p = controller ?: return
        if (p.mediaItemCount > 0) return
        runCatching {
            val saved = prefs.queue.first()
            if (saved.isBlank()) return@runCatching
            val json = JSONObject(saved)
            val idsJson = json.getJSONArray("ids")
            val ids = (0 until idsJson.length()).map { idsJson.getString(it) }
            if (ids.isEmpty()) return@runCatching
            // Room/SQLite bind parameters are limited; restore large queues in safe batches.
            val items = ids.chunked(500).flatMap { batch -> dao.songsByIds(batch) }.associateBy { it.id }
            val restored = ids.mapNotNull { items[it] }.filter { it.playable && (it.source != "local" || app.local.hasPermission()) }
            if (restored.isEmpty()) return@runCatching
            val index = QueueIndexPolicy.restoredCurrentIndex(json.optInt("index", 0), restored.size) ?: return@runCatching
            p.setMediaItems(restored.map(::media), index, json.optLong("position", 0))
            p.repeatMode = json.optInt("repeat", prefs.repeat.first()).coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
            p.shuffleModeEnabled = json.optBoolean("shuffle", prefs.shuffle.first())
            p.setPlaybackSpeed(json.optDouble("speed", prefs.speed.first().toDouble()).toFloat().coerceIn(0.25f, 2f))
            p.prepare() // Restores paused; never autoplays after process restart.
            loadLyrics(restored[index].id)
        }.onFailure { message("Queue sebelumnya tidak dapat dipulihkan: ${it.localizedMessage}") }
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        if (dao.isFavorite(id)) dao.unfavorite(id) else dao.favorite(FavoriteEntity(id))
    }
    fun toggleFavorite(song: SongEntity) = viewModelScope.launch {
        dao.upsertSong(song)
        if (dao.isFavorite(song.id)) dao.unfavorite(song.id) else dao.favorite(FavoriteEntity(song.id))
    }
    fun createPlaylist(name: String) = viewModelScope.launch {
        if (name.isBlank()) { message("Nama playlist wajib diisi"); return@launch }
        dao.addPlaylist(PlaylistEntity(name = name.trim()))
    }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.renamePlaylist(id, name.trim())
    }
    fun deletePlaylist(id: Long) = viewModelScope.launch { dao.clearPlaylist(id); dao.deletePlaylist(id) }
    fun addToPlaylist(playlistId: Long, song: SongEntity) = viewModelScope.launch {
        dao.upsertSong(song)
        val inserted = dao.addToPlaylist(PlaylistSongEntity(playlistId, song.id, dao.nextPlaylistPosition(playlistId)))
        message(if (inserted == -1L) "Lagu sudah ada di playlist" else "Ditambahkan ke playlist")
    }
    fun removeFromPlaylist(playlistId: Long, songId: String) = viewModelScope.launch {
        dao.removeFromPlaylist(playlistId, songId)
    }
    fun playlistSongs(id: Long): Flow<List<SongEntity>> = dao.playlistSongs(id)
    fun setSort(value: String) = viewModelScope.launch { prefs.setSort(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { prefs.setReduceMotion(value) }
    fun setTheme(value: String) = viewModelScope.launch { prefs.setTheme(value) }
    fun setUiStyle(value: String) = viewModelScope.launch { prefs.setUiStyle(value) }

    private fun loadLyrics(id: String) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            lyrics.value = null
            loadingLyrics.value = true
            try {
                val song = dao.song(id)
                if (song != null) lyrics.value = app.lyrics.get(song.title, song.artist)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message("Lirik gagal dimuat: ${failure.localizedMessage ?: "Coba lagi nanti."}")
            } finally {
                if (currentCoroutineContext().isActive) loadingLyrics.value = false
            }
        }
    }
    fun refreshLyrics() { currentId.value?.let(::loadLyrics) }
    fun seekToLyrics(timeMs: Long) { seek(timeMs.coerceAtLeast(0)) }

    fun setSleepTimer(minutes: Int) {
        val safeMinutes = minutes.coerceIn(0, 24 * 60)
        val deadline = if (safeMinutes == 0) 0L else System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(safeMinutes.toLong())
        viewModelScope.launch { prefs.setSleepDeadline(deadline) }
        if (deadline == 0L) {
            sleepJob?.cancel()
            sleepMinutes.value = 0
        } else {
            scheduleSleepDeadline(deadline)
        }
    }

    private fun scheduleSleepDeadline(deadlineEpochMs: Long) {
        sleepJob?.cancel()
        val remaining = id.novamusic.app.player.SleepTimerPolicy.remainingMillis(deadlineEpochMs, System.currentTimeMillis())
        sleepMinutes.value = id.novamusic.app.player.SleepTimerPolicy.remainingMinutes(deadlineEpochMs, System.currentTimeMillis())
        sleepJob = viewModelScope.launch {
            if (remaining > 0L) delay(remaining)
            // On cold start the service connection can complete after the persisted deadline.
            // Keep the expired deadline until the controller is available, then pause playback.
            while (controller == null && isActive) delay(250)
            controller?.pause()
            sleepMinutes.value = 0
            syncPlayer()
            prefs.setSleepDeadline(0L)
        }
    }

    fun download(song: SongEntity) = viewModelScope.launch {
        if (!song.downloadable) {
            message("Sumber ini tidak menyediakan unduhan yang diizinkan"); return@launch
        }
        runCatching {
            val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val safeName = song.title.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(70)
            val extension = Uri.parse(song.uri).lastPathSegment?.substringAfterLast('.', "mp3")?.lowercase()
                ?.takeIf { it in listOf("mp3", "flac", "ogg", "wav", "m4a", "opus") } ?: "mp3"
            val request = DownloadManager.Request(Uri.parse(song.uri))
                .setTitle(song.title).setDescription("${song.source} | ${song.license}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_MUSIC, "$safeName-${System.currentTimeMillis()}.$extension")
            val id = manager.enqueue(request)
            dao.addDownload(DownloadEntity(id, song.id, song.title, song.source, song.license))
        }.onSuccess { message("Unduhan dimulai") }
            .onFailure { message("Unduhan gagal: ${it.localizedMessage}") }
    }

    fun refreshDownloads() = viewModelScope.launch(Dispatchers.IO) {
        val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val result = mutableMapOf<Long, Pair<Int, Int>>()
        downloads.first().forEach { d ->
            manager.query(DownloadManager.Query().setFilterById(d.id))?.use { c ->
                if (c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    result[d.id] = status to if (total > 0) ((bytes * 100 / total).toInt()) else 0
                }
            }
        }
        downloadStatus.value = result
    }
    fun cancelDownload(id: Long) = viewModelScope.launch {
        (app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        dao.removeDownload(id); refreshDownloads()
    }
    fun clearArtworkCache() = viewModelScope.launch(Dispatchers.IO) {
        app.imageLoader.diskCache?.clear()
        message("Cache gambar dibersihkan")
    }

    override fun onCleared() {
        app.contentResolver.unregisterContentObserver(mediaObserver)
        controller?.removeListener(listener)
        searchJob?.cancel()
        lyricsJob?.cancel()
        sleepJob?.cancel()
        scanJob?.cancel()
        volumePersistJob?.cancel()
        MediaController.releaseFuture(future)
        super.onCleared()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NovaViewModel(app) as T
    }
}
