package id.novamusic.app.ui

import android.app.DownloadManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import id.novamusic.app.NovaViewModel
import id.novamusic.app.data.PlaylistEntity
import id.novamusic.app.data.SongEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private fun time(ms: Long): String {
    val sec = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(sec / 60, sec % 60)
}

@Composable fun NowPlayingScreen(vm: NovaViewModel, song: SongEntity?, favorite: Boolean, reduceMotion: Boolean) {
    val playing by vm.playing.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val duration by vm.duration.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val repeat by vm.repeat.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val sleep by vm.sleepMinutes.collectAsStateWithLifecycle()
    var seeking by remember(song?.id) { mutableFloatStateOf(-1f) }
    var timerMenu by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing, reduceMotion) {
        if (playing && !reduceMotion) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, animationSpec = tween(20000, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Header("Now Playing", song?.source ?: "Music player") { vm.navigate("Home") }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(1.1f), contentAlignment = Alignment.Center) {
                AlbumArt(song, Modifier.fillMaxWidth(.84f).aspectRatio(1f).rotate(rotation.value % 360f))
            }
            Spacer(Modifier.height(22.dp))
            Text(song?.title ?: "No track selected", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, color = Forest(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song?.artist ?: "Choose a song from your library", color = Muted(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Slider(value = if (seeking >= 0) seeking else if (duration > 0) (progress.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { seeking = it }, onValueChangeFinished = {
                    if (seeking >= 0 && duration > 0) vm.seek((seeking * duration).toLong()); seeking = -1f
                }, enabled = duration > 0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time(progress), color = Muted(), fontSize = 12.sp)
                Text(time(duration), color = Muted(), fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) Olive() else Muted()) }
                IconButton(onClick = vm::previous) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(34.dp)) }
                FilledIconButton(onClick = vm::toggle, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Forest())) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", Modifier.size(36.dp))
                }
                IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(34.dp)) }
                IconButton(onClick = vm::cycleRepeat) { Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat mode", tint = if (repeat != Player.REPEAT_MODE_OFF) Olive() else Muted()) }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { song?.let { vm.toggleFavorite(it.id) } }, enabled = song != null) {
                    Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = Olive())
                }
                IconButton(onClick = { vm.navigate("Queue") }) { Icon(Icons.Rounded.QueueMusic, "Queue") }
                IconButton(onClick = { vm.refreshLyrics(); vm.navigate("Lyrics") }) { Icon(Icons.Rounded.MusicNote, "Lyrics") }
                Box {
                    IconButton(onClick = { timerMenu = true }) { Icon(Icons.Rounded.Timer, "Sleep timer", tint = if (sleep > 0) Olive() else Forest()) }
                    DropdownMenu(expanded = timerMenu, onDismissRequest = { timerMenu = false }) {
                        listOf(0, 5, 15, 30, 60).forEach { minutes ->
                            DropdownMenuItem(text = { Text(if (minutes == 0) "Timer off" else "$minutes minutes") }, onClick = {
                                vm.setSleepTimer(minutes); timerMenu = false
                            })
                        }
                    }
                }
            }
            if (sleep > 0) Text("Sleep timer: $sleep min", color = Olive(), fontSize = 12.sp)
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Playback", fontWeight = FontWeight.Bold, color = Forest())
                Text("Speed: ${speed}x", color = Muted())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(.75f, 1f, 1.25f, 1.5f).forEach { s ->
                        FilterChip(selected = speed == s, onClick = { vm.setSpeed(s) }, label = { Text("${s}x", fontSize = 11.sp) })
                    }
                }
                Text("App volume", color = Muted())
                Slider(value = volume, onValueChange = vm::setVolume)
                song?.let { Text("Source: ${it.source}", color = Muted(), fontSize = 12.sp) }
                song?.license?.takeIf { it.isNotBlank() }?.let { Text("Rights: $it", color = Muted(), fontSize = 11.sp) }
            }
        }
    }
}

@Composable fun QueueScreen(vm: NovaViewModel, all: Map<String, SongEntity>) {
    val ids by vm.queue.collectAsStateWithLifecycle()
    val index by vm.currentIndex.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val repeat by vm.repeat.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Header("Queue", "${ids.size} tracks in playback order") { vm.navigate("Now Playing") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, null); Text(if (shuffle) " On" else " Off") }
                OutlinedButton(onClick = vm::cycleRepeat) { Icon(Icons.Rounded.Repeat, null); Text(" ${listOf("Off", "One", "All").getOrElse(repeat) { "Off" }}") }
                TextButton(onClick = vm::clearQueue, enabled = ids.isNotEmpty()) { Text("Clear") }
            }
            Text("Use arrows to reorder without losing the current track.", color = Muted(), fontSize = 12.sp)
        }
        items(ids.size, key = { "queue:$it:${ids[it]}" }) { i ->
            val song = all[ids[i]]
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == index) "♫" else "${i + 1}", color = Olive(), modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: ids[i], maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(song?.artist ?: "Unknown", color = Muted(), fontSize = 12.sp)
                }
                IconButton(onClick = { vm.moveQueue(i, i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                IconButton(onClick = { vm.moveQueue(i, i + 1) }, enabled = i < ids.lastIndex) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                IconButton(onClick = { vm.removeQueue(i) }) { Icon(Icons.Rounded.Close, "Remove") }
            }
            HorizontalDivider(color = Sage().copy(alpha = .25f))
        }
        if (ids.isEmpty()) item { EmptyText("Your queue is empty. Play a song to begin.") }
    }
}

@Composable fun LyricsScreen(vm: NovaViewModel, song: SongEntity?) {
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val loading by vm.loadingLyrics.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    val synced = lyrics?.synced.orEmpty()
    val active = synced.indexOfLast { it.timeMs <= progress }.coerceAtLeast(0)
    LaunchedEffect(active, synced.size) { if (synced.isNotEmpty()) state.animateScrollToItem(active) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("Lyrics", song?.title ?: "No song") { vm.navigate("Now Playing") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Lyrics provided by LRCLIB where available", color = Muted(), fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::refreshLyrics, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                Text(" Retry")
            }
        }
        Spacer(Modifier.height(18.dp))
        if (loading) CircularProgressIndicator()
        else if (synced.isNotEmpty()) LazyColumn(state = state, contentPadding = PaddingValues(vertical = 30.dp)) {
            items(synced.size) { i ->
                Text(synced[i].text.ifBlank { "♪" },
                    modifier = Modifier.fillMaxWidth().clickable { vm.seekToLyrics(synced[i].timeMs) }.padding(vertical = 12.dp),
                    color = if (i == active) Forest() else Muted().copy(alpha = .65f),
                    fontSize = if (i == active) 23.sp else 19.sp,
                    fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal)
            }
        }
        else if (!lyrics?.plain.isNullOrBlank()) Text(lyrics?.plain.orEmpty(), modifier = Modifier.verticalScroll(rememberScrollState()),
            lineHeight = 29.sp, fontSize = 18.sp)
        else EmptyText("Lyrics unavailable for this track.")
    }
}

@Composable fun PlaylistScreen(vm: NovaViewModel, playlists: List<PlaylistEntity>, favorites: Set<String>) {
    val selected by vm.selectedPlaylist.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    val chosen = playlists.firstOrNull { it.id == selected }
    val songsFlow = remember(selected) {
        selected?.let { vm.playlistSongs(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList<SongEntity>())
    }
    val playlistSongs by songsFlow.collectAsState(initial = emptyList())
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text(if (chosen == null) "Create playlist" else "Rename playlist") },
        text = { OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Playlist name") }) },
        confirmButton = { TextButton(onClick = {
            if (chosen == null) vm.createPlaylist(name) else vm.renamePlaylist(chosen.id, name)
            editing = false
        }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } })
    if (deleting && chosen != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete ${chosen.name}?") },
        text = { Text("This removes the playlist, not the original audio files.") },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(chosen.id); vm.selectedPlaylist.value = null; deleting = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Header(chosen?.name ?: "Playlists", "Organize your sound") {
                if (selected != null) {
                    vm.selectedPlaylist.value = null
                } else {
                    vm.navigate("Device")
                }
            }
            if (chosen == null) Button(onClick = { name = ""; editing = true }) { Icon(Icons.Rounded.Add, null); Text(" Create playlist") }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.playAll(playlistSongs) }, enabled = playlistSongs.isNotEmpty()) { Text("Play all") }
                OutlinedButton(onClick = { name = chosen.name; editing = true }) { Icon(Icons.Rounded.Edit, "Rename") }
                OutlinedButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, "Delete") }
            }
        }
        if (chosen == null) items(playlists, key = { it.id }) { list ->
            ListItem(headlineContent = { Text(list.name) }, leadingContent = { Icon(Icons.Rounded.QueueMusic, null) },
                modifier = Modifier.clickable { vm.selectedPlaylist.value = list.id })
        } else items(playlistSongs, key = { it.id }) { song ->
            SongRow(song, vm, playlistSongs, song.id in favorites, playlists, chosen.id)
        }
        if (chosen != null && playlistSongs.isEmpty()) item { EmptyText("Add songs using the three-dot menu in Library or Search.") }
    }
}

@Composable fun DownloadsScreen(vm: NovaViewModel) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val status by vm.downloadStatus.collectAsStateWithLifecycle()
    LaunchedEffect(downloads) { while (isActive) { vm.refreshDownloads(); delay(2000) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Header("Downloads", "Only explicitly licensed Internet Archive files") { vm.navigate("Settings") }
            OutlinedButton(onClick = vm::refreshDownloads) { Icon(Icons.Rounded.Refresh, null); Text(" Refresh status") }
        }
        items(downloads, key = { it.id }) { d ->
            val (state, pct) = status[d.id] ?: (0 to 0)
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(d.title, fontWeight = FontWeight.Bold)
                Text(d.source, color = Muted(), fontSize = 12.sp)
                Text(d.license, color = Muted(), fontSize = 11.sp, maxLines = 2)
                val label = when (state) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                    DownloadManager.STATUS_RUNNING -> "Downloading $pct%"
                    DownloadManager.STATUS_PENDING -> "Pending"
                    DownloadManager.STATUS_PAUSED -> "Paused by system"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    else -> "Checking status"
                }
                Text(label, color = Olive())
                if (state == DownloadManager.STATUS_RUNNING) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.cancelDownload(d.id) }) { Text(if (state == DownloadManager.STATUS_SUCCESSFUL) "Remove download file" else "Cancel download") }
            }
        }
        if (downloads.isEmpty()) item { EmptyText("Licensed downloads will appear here.") }
    }
}

@Composable fun SettingsScreen(vm: NovaViewModel) {
    val reduceMotion by vm.reduceMotion.collectAsStateWithLifecycle()
    val themeName by vm.theme.collectAsStateWithLifecycle()
    val uiStyle by vm.uiStyle.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val sleep by vm.sleepMinutes.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Header("Settings", "Make NovaMusic yours")
            SectionTitle("Appearance")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Color theme", fontWeight = FontWeight.SemiBold)
                Text("Choose the color mood for the whole app", color = Muted(), fontSize = 12.sp)
                val themes = listOf("Daylight Glass", "Midnight AMOLED", "Ocean Breeze", "Rose Quartz", "Lavender Haze", "Sunset")
                themes.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { option ->
                            FilterChip(selected = themeName == option, onClick = { vm.setTheme(option) },
                                label = { Text(option, maxLines = 1) }, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Interface style", fontWeight = FontWeight.SemiBold)
                Text("Choose Liquid Glass, classic glass, minimal or compact", color = Muted(), fontSize = 12.sp)
                listOf("Liquid Glass", "Glass", "Minimal", "Compact").chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { option ->
                            FilterChip(selected = uiStyle == option, onClick = { vm.setUiStyle(option) },
                                label = { Text(option, maxLines = 1) }, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reduce animations", Modifier.weight(1f))
                    Switch(checked = reduceMotion, onCheckedChange = vm::setReduceMotion)
                }
            }
            SectionTitle("Audio")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Playback speed: ${speed}x")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(.75f, 1f, 1.25f, 1.5f).forEach { s ->
                        FilterChip(selected = speed == s, onClick = { vm.setSpeed(s) }, label = { Text("${s}x") })
                    }
                }
                Text("App volume")
                Slider(value = volume, onValueChange = vm::setVolume)
            }
            SectionTitle("Playback")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Sleep timer: ${if (sleep == 0) "Off" else "$sleep minutes"}")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 15, 30, 60).forEach { m ->
                        FilterChip(selected = sleep == m, onClick = { vm.setSleepTimer(m) }, label = { Text(if (m == 0) "Off" else "$m min") })
                    }
                }
            }
            SectionTitle("Library")
            GlassCard(Modifier.fillMaxWidth()) {
                Button(onClick = vm::refreshLibrary) { Icon(Icons.Rounded.Refresh, null); Text(" Rescan local music") }
                Text("If permission was denied, enable audio access in Android app settings.", color = Muted(), fontSize = 12.sp)
            }
            SectionTitle("Downloads & storage")
            GlassCard(Modifier.fillMaxWidth()) {
                Button(onClick = { vm.navigate("Downloads") }) { Icon(Icons.Rounded.Download, null); Text(" View downloads") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::clearArtworkCache) { Text("Clear artwork cache") }
            }
            SectionTitle("About")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("NovaMusic 1.1.0", fontWeight = FontWeight.Bold)
                Text("Your sound, your space.", color = Muted())
                Text("7 online music catalogues · Local audio · LRCLIB", color = Muted(), fontSize = 12.sp)
                Text("Streaming and downloads require independently verified rights.", color = Muted(), fontSize = 12.sp)
            }
        }
    }
}
