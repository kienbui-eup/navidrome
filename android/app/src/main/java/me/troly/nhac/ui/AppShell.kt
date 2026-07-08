package me.troly.nhac.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.Playlist
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.components.DeletePlaylistDialog
import me.troly.nhac.ui.player.MiniPlayer
import me.troly.nhac.ui.player.NowPlayingScreen
import me.troly.nhac.ui.screens.AlbumDetailScreen
import me.troly.nhac.ui.screens.ArtistDetailScreen
import me.troly.nhac.ui.screens.HomeScreen
import me.troly.nhac.ui.screens.LibraryScreen
import me.troly.nhac.ui.screens.PlaylistDetailScreen
import me.troly.nhac.ui.screens.SearchScreen
import me.troly.nhac.ui.screens.SettingsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Trang chủ", Icons.Filled.Home),
    SEARCH("Tìm kiếm", Icons.Filled.Search),
    LIBRARY("Thư viện", Icons.Filled.LibraryMusic),
    SETTINGS("Cài đặt", Icons.Filled.Settings),
}

@Composable
fun AppShell() {
    val isTv = LocalIsTv.current
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var albumId by remember { mutableStateOf<String?>(null) }
    var artistId by remember { mutableStateOf<String?>(null) }
    var playlistId by remember { mutableStateOf<String?>(null) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val openAlbum: (String) -> Unit = { albumId = it }
    val openNow = { showNowPlaying = true }

    // Play a picked playlist from the quick-picker sheet.
    val playPlaylist: (Playlist) -> Unit = { pl ->
        scope.launch {
            val songs = runCatching { repo.playlist(pl.id)?.entry }.getOrNull().orEmpty()
            showPlaylistPicker = false
            if (songs.isNotEmpty()) {
                player.play(songs, 0)
                showNowPlaying = true
            }
        }
        Unit
    }

    // Quick play: recently played album, else the top playlist.
    val quickPlay = {
        scope.launch {
            val songs = runCatching {
                val recent = repo.albums("recent", 1).firstOrNull()
                val fromRecent = recent?.let { repo.album(it.id)?.song }.orEmpty()
                fromRecent.ifEmpty {
                    val top = repo.playlists().firstOrNull()
                    top?.let { repo.playlist(it.id)?.entry }.orEmpty()
                }
            }.getOrDefault(emptyList())
            if (songs.isNotEmpty()) {
                player.play(songs, 0)
                showNowPlaying = true
            }
        }
        Unit
    }

    val content: @Composable (Modifier) -> Unit = { m ->
        Box(m.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    slideIntoContainer(
                        towards = if (direction == 1) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    ) togetherWith slideOutOfContainer(
                        towards = if (direction == 1) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                },
                label = "tabTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetTab ->
                Box(Modifier.fillMaxSize()) {
                    when (targetTab) {
                        Tab.HOME -> HomeScreen(onAlbum = { openAlbum(it.id) })
                        Tab.SEARCH -> SearchScreen(onAlbum = { openAlbum(it.id) }, onNowPlaying = openNow)
                        Tab.LIBRARY -> LibraryScreen(
                            onAlbum = openAlbum,
                            onArtist = { artistId = it },
                            onPlaylist = { playlistId = it },
                        )
                        Tab.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            if (isTv) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = false, onClick = { quickPlay() },
                            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Nghe nhanh") },
                            label = { Text("Nghe nhanh") },
                        )
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t, onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        content(Modifier.weight(1f))
                        MiniPlayer(onExpand = openNow)
                    }
                }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        Column {
                            MiniPlayer(onExpand = openNow)
                            CompactBottomBar(
                                current = tab,
                                onSelect = { tab = it },
                                onOpenPlaylists = { showPlaylistPicker = true },
                            )
                        }
                    },
                ) { padding -> content(Modifier.padding(padding)) }
            }
        }

        // Overlays (later = higher in the stack)
        artistId?.let { id ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ArtistDetailScreen(id, onBack = { artistId = null }, onAlbum = openAlbum)
            }
        }
        playlistId?.let { id ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PlaylistDetailScreen(id, onBack = { playlistId = null }, onNowPlaying = openNow)
            }
        }
        albumId?.let { id ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AlbumDetailScreen(id, onBack = { albumId = null }, onNowPlaying = openNow)
            }
        }
        if (showNowPlaying) {
            NowPlayingScreen(onClose = { showNowPlaying = false })
        }
        if (showPlaylistPicker) {
            PlaylistPickerSheet(
                onDismiss = { showPlaylistPicker = false },
                onQuickPlay = { showPlaylistPicker = false; quickPlay() },
                onPick = playPlaylist,
            )
        }
    }
}

/**
 * Quick playlist picker shown from the center bottom-bar button. Lists existing
 * playlists so the user picks one to play, instead of the old single-tap default.
 * The first row keeps the previous "Nghe nhanh" behaviour as an explicit choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    onDismiss: () -> Unit,
    onQuickPlay: () -> Unit,
    onPick: (Playlist) -> Unit,
) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var toDelete by remember { mutableStateOf<Playlist?>(null) }
    LaunchedEffect(Unit) {
        playlists = runCatching { repo.playlists() }.getOrDefault(emptyList())
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Chọn playlist", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onQuickPlay)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Text("Nghe nhanh", style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        when {
            playlists == null -> Box(
                Modifier.fillMaxWidth().padding(32.dp), Alignment.Center,
            ) { CircularProgressIndicator() }
            playlists!!.isEmpty() -> Text(
                "Chưa có playlist nào", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
                items(playlists!!, key = { it.id }) { pl ->
                    PlaylistRow(pl, onClick = { onPick(pl) }, onDelete = { toDelete = pl })
                }
            }
        }
    }

    toDelete?.let { pl ->
        DeletePlaylistDialog(
            name = pl.name,
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                scope.launch {
                    val ok = runCatching { repo.deletePlaylist(pl.id) }.isSuccess
                    Toast.makeText(context,
                        if (ok) "Đã xoá \"${pl.name}\"" else "Không xoá được, thử lại",
                        Toast.LENGTH_SHORT).show()
                    if (ok) playlists = playlists?.filterNot { it.id == pl.id }
                }
            },
        )
    }
}

@Composable
private fun PlaylistRow(pl: Playlist, onClick: () -> Unit, onDelete: () -> Unit) {
    val repo = LocalRepo.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = repo.config.coverArtUrl(pl.coverArt, 120),
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pl.name, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            pl.songCount?.let {
                Text("$it bài", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, "Xoá playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Compact bottom bar (58dp) — HOME · SEARCH · [playlists] · LIBRARY · SETTINGS.
 * The center button opens a quick playlist picker (see PlaylistPickerSheet).
 */
@Composable
private fun CompactBottomBar(current: Tab, onSelect: (Tab) -> Unit, onOpenPlaylists: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
        // Edge-to-edge: lift the bar above the system navigation/gesture bar so items
        // aren't covered. The Surface tint still fills down to the screen edge.
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem(Tab.HOME, current, onSelect, Modifier.weight(1f))
            BarItem(Tab.SEARCH, current, onSelect, Modifier.weight(1f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(46.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onOpenPlaylists),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlist",
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp),
                    )
                }
            }
            BarItem(Tab.LIBRARY, current, onSelect, Modifier.weight(1f))
            BarItem(Tab.SETTINGS, current, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BarItem(tab: Tab, current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val selected = tab == current
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxSize()
            .clickable(interactionSource = source, indication = null) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            tab.label, color = tint, fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
