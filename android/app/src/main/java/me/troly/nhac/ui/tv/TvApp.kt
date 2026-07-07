package me.troly.nhac.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import me.troly.nhac.ui.player.NowPlayingScreen
import me.troly.nhac.ui.screens.AlbumDetailScreen
import me.troly.nhac.ui.screens.ArtistDetailScreen
import me.troly.nhac.ui.screens.LibraryScreen
import me.troly.nhac.ui.screens.PlaylistDetailScreen
import me.troly.nhac.ui.screens.SearchScreen

private data class TvTabItem(val label: String, val icon: ImageVector)

/**
 * Android TV (X96 box) shell. Tối ưu hóa giao diện sử dụng thanh điều hướng bên trái (NavigationRail)
 * thay vì TabRow phía trên, giúp việc di chuyển bằng điều khiển D-pad trở nên cực kỳ thuận tiện và chuẩn Leanback.
 */
@Composable
fun TvApp() {
    var tab by remember { mutableIntStateOf(0) }
    var albumId by remember { mutableStateOf<String?>(null) }
    var artistId by remember { mutableStateOf<String?>(null) }
    var playlistId by remember { mutableStateOf<String?>(null) }
    var showNowPlaying by remember { mutableStateOf(false) }

    val openAlbum: (String) -> Unit = { albumId = it }
    val openNow = { showNowPlaying = true }
    
    val tabs = listOf(
        TvTabItem("Trang chủ", Icons.Filled.Home),
        TvTabItem("Tìm kiếm", Icons.Filled.Search),
        TvTabItem("Thư viện", Icons.Filled.LibraryMusic)
    )

    Box(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxSize()) {
                // Left NavigationRail for easy TV D-pad navigation
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.background,
                    header = {
                        Text(
                            text = "vi2play",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp)
                        )
                    }
                ) {
                    tabs.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) }
                        )
                    }
                }
                
                // Right main content area
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> TvTheme { TvHome(onAlbum = openAlbum) }
                        1 -> SearchScreen(onAlbum = { openAlbum(it.id) }, onNowPlaying = openNow)
                        else -> LibraryScreen(
                            onAlbum = openAlbum,
                            onArtist = { artistId = it },
                            onPlaylist = { playlistId = it },
                        )
                    }
                }
            }
        }

        // Overlay screens
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
        if (showNowPlaying) NowPlayingScreen(onClose = { showNowPlaying = false })
    }
}
