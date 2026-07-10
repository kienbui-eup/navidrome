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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.NavigationRailItemDefaults

import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect

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

    val isOverlayActive = artistId != null || playlistId != null || albumId != null || showNowPlaying

    Box(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = !isOverlayActive }
            ) {
                // Left NavigationRail for easy TV D-pad navigation
                NavigationRail(
                    containerColor = Color(0xFF111014),
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Hearing,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "DECENT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                ) {
                    tabs.forEachIndexed { i, t ->
                        val selected = tab == i
                        NavigationRailItem(
                            selected = selected,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = Color(0xFFA79C86),
                                unselectedTextColor = Color(0xFFA79C86),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
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
            val focusRequester = remember(id) { FocusRequester() }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
                color = MaterialTheme.colorScheme.background
            ) {
                ArtistDetailScreen(id, onBack = { artistId = null }, onAlbum = openAlbum)
            }
            LaunchedEffect(id) {
                focusRequester.requestFocus()
            }
        }
        playlistId?.let { id ->
            val focusRequester = remember(id) { FocusRequester() }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
                color = MaterialTheme.colorScheme.background
            ) {
                PlaylistDetailScreen(id, onBack = { playlistId = null }, onNowPlaying = openNow)
            }
            LaunchedEffect(id) {
                focusRequester.requestFocus()
            }
        }
        albumId?.let { id ->
            val focusRequester = remember(id) { FocusRequester() }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
                color = MaterialTheme.colorScheme.background
            ) {
                AlbumDetailScreen(id, onBack = { albumId = null }, onNowPlaying = openNow, onArtistClick = { artistId = it })
            }
            LaunchedEffect(id) {
                focusRequester.requestFocus()
            }
        }
        if (showNowPlaying) {
            val focusRequester = remember { FocusRequester() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
            ) {
                NowPlayingScreen(onClose = { showNowPlaying = false })
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}

