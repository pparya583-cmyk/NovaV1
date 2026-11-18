package id.novamusic.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import id.novamusic.app.NovaViewModel
import id.novamusic.app.R
import id.novamusic.app.data.*
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val tabs = listOf("Home", "Online", "Device", "Favorites", "Settings")
private val tabIcons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.LibraryMusic, Icons.Rounded.Favorite, Icons.Rounded.Settings)

@Composable fun NovaRoot(vm: NovaViewModel) {
    val themeName by vm.theme.collectAsStateWithLifecycle()
    val uiStyle by vm.uiStyle.collectAsStateWithLifecycle()
    NovaTheme(themeName, uiStyle) {
        var splash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(1900); splash = false }
        if (splash) { SplashScreen(); return@NovaTheme }
        val screen by vm.screen.collectAsStateWithLifecycle()
        val songs by vm.songs.collectAsStateWithLifecycle()
        val favorites by vm.favorites.collectAsStateWithLifecycle()
        val playlists by vm.playlists.collectAsStateWithLifecycle()
        val history by vm.history.collectAsStateWithLifecycle()
        val currentId by vm.currentId.collectAsStateWithLifecycle()
        val playing by vm.playing.collectAsStateWithLifecycle()
        val error by vm.error.collectAsStateWithLifecycle()
        val reduceMotion by vm.reduceMotion.collectAsStateWithLifecycle()
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(error) { error?.let { snack.showSnackbar(it); vm.dismissError() } }
        val all = remember(songs) { songs.associateBy { it.id } }
        val current = currentId?.let { all[it] }
        // System back: sub-screens return to their parent instead of closing the app.
        BackHandler(enabled = screen !in tabs) {
            when (screen) {
                "Queue", "Lyrics" -> vm.navigate("Now Playing")
                "Playlist" -> {
                    if (vm.selectedPlaylist.value != null) {
                        vm.selectedPlaylist.value = null
                    } else {
                        vm.navigate("Device")
                    }
                }
                "Downloads" -> vm.navigate("Settings")
                else -> vm.navigate("Home")
            }
            Unit
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            containerColor = Ivory(),
            bottomBar = {
                Column {
                    AnimatedVisibility(currentId != null && screen != "Now Playing" && screen != "Queue" && screen != "Lyrics",
                        enter = fadeIn() + slideInVertically { it }, exit = fadeOut()) {
                        MiniPlayer(
                            vm = vm,
                            song = current,
                            isFavorite = favorites.any { it.songId == currentId },
                            playlists = playlists
                        )
                    }
                    if (screen in tabs) NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f), tonalElevation = 4.dp) {
                        tabs.forEachIndexed { i, tab ->
                            NavigationBarItem(selected = screen == tab, onClick = { vm.navigate(tab) },
                                icon = { Icon(tabIcons[i], contentDescription = tab) },
                                label = { Text(tab, fontSize = 10.sp) })
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().daylight().padding(padding)) {
                AnimatedContent(targetState = screen, label = "navigation",
                    transitionSpec = {
                        if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else (fadeIn(tween(220)) + scaleIn(initialScale = .94f, animationSpec = tween(260)) + slideInVertically(initialOffsetY = { it / 18 }, animationSpec = tween(240))) togetherWith
                            (fadeOut(tween(150)) + scaleOut(targetScale = .96f, animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { -it / 24 }, animationSpec = tween(180)))
                    }) { page ->
                    when (page) {
                        "Home" -> HomeScreen(vm, songs.filter { it.source != "local" }, history.filter { h -> songs.any { it.id == h.songId && it.source != "local" } }, playlists, favorites.map { it.songId }.toSet())
                        "Online" -> SearchScreen(vm, favorites.map { it.songId }.toSet(), playlists)
                        "Device" -> LibraryScreen(vm, songs.filter { it.source == "local" }, favorites.map { it.songId }.toSet(), playlists)
                        "Favorites" -> FavoritesScreen(vm, songs.filter { it.id in favorites.map { f -> f.songId } }, playlists)
                        "Settings" -> SettingsScreen(vm)
                        "Now Playing" -> NowPlayingScreen(vm, current, favorites.any { it.songId == currentId }, reduceMotion)
                        "Queue" -> QueueScreen(vm, all)
                        "Lyrics" -> LyricsScreen(vm, current)
                        "Playlist" -> PlaylistScreen(vm, playlists, favorites.map { it.songId }.toSet())
                        "Downloads" -> DownloadsScreen(vm)
                        else -> HomeScreen(vm, songs.filter { it.source != "local" }, history.filter { h -> songs.any { it.id == h.songId && it.source != "local" } }, playlists, emptySet())
                    }
                }
                if (uiStyle == "Liquid Glass" && currentId != null && screen != "Now Playing") FloatingBubble(vm)
            }
        }
    }
}

@Composable private fun SplashScreen() {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(if (appeared) 1f else .72f, tween(800), label = "logo scale")
    val opacity by animateFloatAsState(if (appeared) 1f else 0f, tween(850), label = "logo fade")
    Box(Modifier.fillMaxSize().daylight(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), "NovaMusic logo",
                Modifier.size(188.dp).scale(scale).alpha(opacity))
            Text("NovaMusic", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Forest())
            Text("Your sound, your space.", color = Muted())
            Equalizer(playing = true, motion = true)
        }
    }
}

@Composable fun Header(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Forest())
            subtitle?.let { Text(it, color = Muted(), fontSize = 13.sp) }
        }
    }
}

@Composable fun SectionTitle(title: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Forest())
        if (action != null && onClick != null) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable fun AlbumArt(song: SongEntity?, modifier: Modifier = Modifier) {
    if (song?.artwork != null) AsyncImage(model = song.artwork, contentDescription = "Artwork ${song.title}",
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Sage().copy(alpha = .28f)),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_launcher_foreground))
    else Box(modifier.clip(RoundedCornerShape(16.dp)).background(Sage().copy(alpha = .3f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = Forest())
    }
}

@Composable fun SongRow(song: SongEntity, vm: NovaViewModel, contextList: List<SongEntity>,
                                 favorite: Boolean, playlists: List<PlaylistEntity> = emptyList(), playlistId: Long? = null) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable {
        if (song.playable) vm.playSong(song, contextList)
        else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(song.uri))) }
    }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AlbumArt(song, Modifier.size(50.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${song.artist} · ${if (song.source == "local") "Local device" else song.source}",
                fontSize = 12.sp, color = Muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!song.playable) Text("Metadata only · tap to open source", color = Olive(), fontSize = 11.sp)
        }
        IconButton(onClick = { vm.toggleFavorite(song) }) {
            Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Favorite", tint = if (favorite) Olive() else Muted())
        }
        Box {
            IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, "Song options") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                @Composable fun entry(label: String, onClick: () -> Unit) {
                    DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onClick() })
                }
                if (song.playable) {
                    entry("Play next") { vm.addToQueue(song, true) }
                    entry("Add to queue") { vm.addToQueue(song) }
                } else {
                    entry("Open source") { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(song.uri))) } }
                }
                playlists.forEach { list -> entry("Add to ${list.name}") { vm.addToPlaylist(list.id, song) } }
                if (playlistId != null) entry("Remove from playlist") { vm.removeFromPlaylist(playlistId, song.id) }
                if (song.downloadable) entry("Download licensed audio") { vm.download(song) }
                val context = LocalContext.current
                entry("Share metadata") {
                    val text = "${song.title} · ${song.artist} (${song.source})\n${song.license}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }, "Share song"))
                }
            }
        }
    }
    HorizontalDivider(color = Forest().copy(alpha = .06f))
}

@Composable private fun HomeScreen(vm: NovaViewModel, songs: List<SongEntity>, history: List<PlayHistoryEntity>,
                                   playlists: List<PlaylistEntity>, favorites: Set<String>) {
    val byId = songs.associateBy { it.id }
    val recent = history.mapNotNull { byId[it.songId] }.distinctBy { it.id }.take(5)
    val mostPlayed = history.groupingBy { it.songId }.eachCount().entries.sortedByDescending { it.value }
        .mapNotNull { byId[it.key] }.take(5)
    val added = songs.sortedByDescending { it.addedAt }.take(6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 22.dp, 20.dp, 24.dp)) {
        item {
            Header("NovaMusic", "Good day. Your sound, your space.")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Your sound space", color = Olive(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("A quiet place for your music.", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Forest())
                Text("${songs.count { it.source == "local" }} local tracks · ${playlists.size} playlists", color = Muted())
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.navigate("Device") }, colors = ButtonDefaults.buttonColors(containerColor = Forest())) {
                    Icon(Icons.Rounded.LibraryMusic, null); Spacer(Modifier.width(8.dp)); Text("Explore device music")
                }
            }
            SectionTitle("Quick access")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Favorites" to Icons.Rounded.Favorite, "Online" to Icons.Rounded.Search, "Downloads" to Icons.Rounded.Download).forEach { (label, icon) ->
                    OutlinedButton(onClick = { vm.navigate(label) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(icon, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 12.sp)
                    }
                }
            }
            SectionTitle("Recently played")
        }
        if (recent.isEmpty()) item { EmptyText("Your listening history will appear here.") }
        items(recent, key = { "recent:${it.id}" }) { SongRow(it, vm, recent, it.id in favorites, playlists) }
        item { SectionTitle("Recommended from online listening") }
        if (mostPlayed.isEmpty()) item { EmptyText("Play a few tracks to personalize this section.") }
        items(mostPlayed, key = { "most:${it.id}" }) { SongRow(it, vm, mostPlayed, it.id in favorites, playlists) }
        item { SectionTitle("Recently added online", "View all") { vm.navigate("Online") } }
        items(added, key = { "added:${it.id}" }) { SongRow(it, vm, added, it.id in favorites, playlists) }
        item { SectionTitle("Your playlists", "Manage") { vm.navigate("Playlist") } }
        items(playlists, key = { "pl:${it.id}" }) { p ->
            ListItem(headlineContent = { Text(p.name) }, leadingContent = { Icon(Icons.Rounded.QueueMusic, null) },
                modifier = Modifier.clickable { vm.selectedPlaylist.value = p.id; vm.navigate("Playlist") })
        }
    }
}

@Composable private fun SearchScreen(vm: NovaViewModel, favorites: Set<String>, playlists: List<PlaylistEntity>) {
    var query by remember { mutableStateOf("") }
    var spotifyPlaylistUrl by remember { mutableStateOf("") }
    val spotifyConnected by vm.spotifyConnected.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val searches by vm.searches.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Header("Online Music", "YouTube catalog + Spotify playlist import · local music stays separate")
            OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Search YouTube catalog") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } })
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.searchOnline(query) }, enabled = query.isNotBlank() && !searching,
                modifier = Modifier.fillMaxWidth()) { Text(if (searching) "Searching…" else "Search YouTube") }
            Spacer(Modifier.height(18.dp))
            SectionTitle("Spotify playlist import")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (spotifyConnected) vm.disconnectSpotify() else vm.connectSpotify() },
                    enabled = !searching, modifier = Modifier.weight(1f)) {
                    Text(if (spotifyConnected) "Disconnect Spotify" else "Connect Spotify")
                }
            }
            OutlinedTextField(spotifyPlaylistUrl, onValueChange = { spotifyPlaylistUrl = it },
                label = { Text("Spotify playlist link") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = { vm.importSpotifyPlaylist(spotifyPlaylistUrl) },
                enabled = spotifyConnected && spotifyPlaylistUrl.isNotBlank() && !searching,
                modifier = Modifier.fillMaxWidth()) { Text("Import Spotify playlist") }
            Text("Spotify imports track metadata only. Imported tracks are not playable from Spotify inside NovaMusic.",
                fontSize = 12.sp, color = Muted())
            Text("Explore Indonesian sounds", color = Forest(), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Indonesian music", "Indonesian pop", "Dangdut", "Indonesian folk").forEach { term ->
                    AssistChip(onClick = { query = term; vm.searchOnline(term) }, label = { Text(term) })
                }
            }
            if (query.isBlank() && searches.isNotEmpty()) {
                SectionTitle("Search history", "Clear") { vm.clearSearchHistory() }
                searches.forEach { h ->
                    TextButton(onClick = { query = h.query; vm.searchOnline(h.query) }) { Text(h.query) }
                }
            }
            SectionTitle("Catalog results · ${online.size} tracks")
        }
        item {
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("YouTube results are catalog metadata only and open in YouTube for playback. Spotify playlist import requires user OAuth; Spotify metadata is not an audio stream.",
                fontSize = 12.sp, color = Muted())
        }
        items(online, key = { "online:${it.id}" }) { SongRow(it, vm, online, it.id in favorites, playlists) }
        if (online.isEmpty() && !searching) item { EmptyText("Search the YouTube catalog. Local files stay under Device.") }
    }
}

@Composable private fun LibraryScreen(vm: NovaViewModel, songs: List<SongEntity>, favorites: Set<String>, playlists: List<PlaylistEntity>) {
    var filter by remember { mutableStateOf("All Songs") }
    val choices = listOf("All Songs", "Albums", "Artists", "Folders", "Genres", "Recently Added")
    val local = songs.filter { it.source == "local" }
    val displayed = when (filter) {
        "Recently Added" -> local.sortedByDescending { it.addedAt }
        else -> local
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Header("On-device music", "Audio files stored on this device only")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = vm::refreshLibrary) { Icon(Icons.Rounded.Refresh, null); Text(" Rescan") }
                OutlinedButton(onClick = { vm.selectedPlaylist.value = null; vm.navigate("Playlist") }) { Text("Playlists") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name) }) }
            }
            SectionTitle("$filter · ${local.size} tracks")
        }
        if (filter in listOf("All Songs", "Recently Added")) {
            items(displayed, key = { it.id }) { SongRow(it, vm, displayed, it.id in favorites, playlists) }
        } else {
            val groups = when (filter) {
                "Albums" -> local.groupBy { it.album }
                "Artists" -> local.groupBy { it.artist }
                "Folders" -> local.groupBy { it.folder.ifBlank { "Other folders" } }
                else -> local.groupBy { it.genre.ifBlank { "Uncategorized" } }
            }
            groups.toSortedMap().forEach { (name, group) ->
                item { Text(name, fontWeight = FontWeight.Bold, color = Forest(), modifier = Modifier.padding(vertical = 12.dp)) }
                items(group, key = { "${filter}:${it.id}" }) { SongRow(it, vm, group, it.id in favorites, playlists) }
            }
        }
        if (local.isEmpty()) item { EmptyText("No local music yet. Grant audio permission, then tap Rescan.") }
    }
}

@Composable private fun FavoritesScreen(vm: NovaViewModel, favorites: List<SongEntity>, playlists: List<PlaylistEntity>) {
    val sort by vm.sort.collectAsStateWithLifecycle()
    val sorted = when (sort) { "Artist" -> favorites.sortedBy { it.artist }; "Newest" -> favorites.sortedByDescending { it.addedAt }; else -> favorites.sortedBy { it.title } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Header("Favorites", "${favorites.size} tracks you love")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.playAll(sorted) }, enabled = sorted.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text(" Play all") }
                OutlinedButton(onClick = { vm.playAll(sorted, true) }, enabled = sorted.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null); Text(" Shuffle") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Title", "Artist", "Newest").forEach { FilterChip(selected = sort == it, onClick = { vm.setSort(it) }, label = { Text(it) }) }
            }
        }
        items(sorted, key = { it.id }) { SongRow(it, vm, sorted, true, playlists) }
        if (sorted.isEmpty()) item { EmptyText("Tap the heart beside a song to save it here.") }
    }
}

@Composable private fun MiniPlayer(
    vm: NovaViewModel,
    song: SongEntity?,
    isFavorite: Boolean,
    playlists: List<PlaylistEntity>
) {
    var showPlaylistMenu by remember { mutableStateOf(false) }
    if (song == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(42.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF321923).copy(alpha = .96f),
                        Color(0xFF713448).copy(alpha = .94f),
                        Color(0xFF4B2638).copy(alpha = .96f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(42.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Artwork/title area opens the full player, like the compact dock in the reference.
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(32.dp))
                .clickable { vm.navigate("Now Playing") }
                .padding(start = 2.dp, end = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box {
                AlbumArt(song, Modifier.size(54.dp).clip(CircleShape))
                Box(
                    Modifier.size(54.dp).border(
                        2.dp, Color.White.copy(alpha = .72f), CircleShape
                    )
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        @Composable
        fun DockAction(icon: ImageVector, label: String, onClick: () -> Unit) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .10f))
                    .border(1.dp, Color.White.copy(alpha = .07f), CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, label, tint = Color.White.copy(alpha = .88f), modifier = Modifier.size(25.dp))
            }
        }

        DockAction(Icons.Rounded.PersonOutline, "Open player") { vm.navigate("Now Playing") }
        Box {
            DockAction(Icons.Rounded.Add, "Add to playlist") { showPlaylistMenu = true }
            DropdownMenu(expanded = showPlaylistMenu, onDismissRequest = { showPlaylistMenu = false }) {
                if (playlists.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No playlists yet") },
                        onClick = { showPlaylistMenu = false }
                    )
                } else {
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to ${playlist.name}") },
                            onClick = {
                                showPlaylistMenu = false
                                vm.addToPlaylist(playlist.id, song)
                            }
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text("Manage playlists") },
                    onClick = { showPlaylistMenu = false; vm.navigate("Playlist") }
                )
            }
        }
        DockAction(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (isFavorite) "Remove favorite" else "Add favorite"
        ) { vm.toggleFavorite(song) }
    }
}

@Composable private fun FloatingBubble(vm: NovaViewModel) {
    // rememberSaveable only knows how to save plain MutableState; MutableFloatState throws when the
    // activity saves its state (rotation, background kill) while the bubble is on screen.
    var x by rememberSaveable { mutableStateOf(0f) }
    var y by rememberSaveable { mutableStateOf(0f) }
    val pulse = rememberInfiniteTransition(label = "liquid-glass-bubble")
    val glow by pulse.animateFloat(
        initialValue = .16f,
        targetValue = .34f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
        label = "bubble-glow"
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier
                .padding(end = 18.dp, bottom = 18.dp)
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(66.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        x += drag.x
                        y += drag.y
                    }
                }
                .clickable { vm.navigate("Now Playing") }
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = .82f),
                            Color(0xFFB9D8D0).copy(alpha = .48f + glow),
                            Color(0xFF6C9E91).copy(alpha = .34f)
                        )
                    ),
                    CircleShape
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = .95f), Color.White.copy(alpha = .22f))
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = .16f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = .34f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    "Drag bubble or open player",
                    tint = Color(0xFF244B43),
                    modifier = Modifier.size(29.dp)
                )
            }
        }
    }
}

@Composable private fun Equalizer(playing: Boolean, motion: Boolean) {
    val infinite = rememberInfiniteTransition(label = "equalizer")
    val heights = (0..3).map { i ->
        infinite.animateFloat(initialValue = 6f + i * 3, targetValue = 20f + i * 2,
            animationSpec = infiniteRepeatable(tween(420 + i * 110), RepeatMode.Reverse), label = "bar$i")
    }
    Row(Modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEach { height ->
            Box(Modifier.width(4.dp).height((if (playing && motion) height.value else 8f).dp)
                .clip(RoundedCornerShape(4.dp)).background(Olive()))
        }
    }
}

@Composable fun EmptyText(text: String) {
    Text(text, Modifier.padding(vertical = 16.dp), color = Muted(), fontSize = 13.sp)
}
