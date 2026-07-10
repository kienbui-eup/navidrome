package me.troly.nhac.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.Album
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.components.AddToPlaylistSheet
import me.troly.nhac.ui.components.CoverImage
import me.troly.nhac.ui.components.SongRow

class AlbumViewModel(private val repo: SubsonicRepository, private val albumId: String) : ViewModel() {
    private val _album = MutableStateFlow<Album?>(null)
    val album = _album.asStateFlow()
    init { 
        refresh() 
        viewModelScope.launch {
            repo.refreshEvent.collect {
                refresh()
            }
        }
    }
    fun refresh() { viewModelScope.launch { runCatching { _album.value = repo.album(albumId) } } }
}

@Composable
fun AlbumDetailScreen(albumId: String, onBack: () -> Unit, onNowPlaying: () -> Unit) {
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: AlbumViewModel = viewModel(key = "album-$albumId") { AlbumViewModel(repo, albumId) }
    val album by vm.album.collectAsState()
    val playState by player.state.collectAsState()

    var pendingAdd by remember { mutableStateOf<List<String>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val selectionMode = selected.isNotEmpty()

    var isAdmin by remember { mutableStateOf(false) }
    var showDeleteAlbumDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<me.troly.nhac.data.subsonic.Song?>(null) }

    LaunchedEffect(repo) {
        isAdmin = repo.checkAdminStatus()
    }

    // In selection mode, Back clears the selection instead of leaving the screen.
    BackHandler(enabled = selectionMode) { selected = emptySet() }
    BackHandler(enabled = !selectionMode, onBack = onBack)

    if (album == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val a = album!!
    val coverUrl = repo.config.coverArtUrl(a.coverArt, 600)
    val toggle = { id: String -> selected = if (id in selected) selected - id else selected + id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (selectionMode) 160.dp else 88.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    // Ambient Fluid moving artwork background
                    me.troly.nhac.ui.player.AnimatedAmbientBackground(artworkUri = coverUrl, modifier = Modifier.matchParentSize())
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp).size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = Color.White,
                                    modifier = Modifier.size(28.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAdmin) {
                                    IconButton(
                                        onClick = { showDeleteAlbumDialog = true },
                                        modifier = Modifier.padding(4.dp).size(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline, "Xoá album",
                                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { pendingAdd = a.song.map { it.id } },
                                    modifier = Modifier.padding(4.dp).size(48.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Thêm album vào playlist",
                                        tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .shadow(16.dp, RoundedCornerShape(20.dp))
                                .background(
                                    color = Color.White.copy(alpha = 0.04f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.01f))
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(vertical = 20.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CoverImage(
                                url = coverUrl, contentDescription = a.name, corner = 16.dp,
                                modifier = Modifier.size(216.dp).shadow(20.dp, RoundedCornerShape(16.dp)),
                            )
                            Text(a.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                color = Color.White, modifier = Modifier.padding(top = 14.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            val totalSecs = remember(a.song) { a.song.fold(0) { acc, s -> acc + (s.duration ?: 0) } }
                            val durationText = remember(totalSecs) {
                                val mins = totalSecs / 60
                                val secs = totalSecs % 60
                                "${mins} phút ${secs} giây"
                            }
                            Text(
                                text = listOfNotNull(
                                    a.artist,
                                    a.year?.toString(),
                                    if (a.song.isNotEmpty()) "${a.song.size} bài • $durationText" else a.songCount?.let { "$it bài" }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC4BBA6),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { player.play(a.song, 0); onNowPlaying() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary),
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Text("Phát", modifier = Modifier.padding(start = 4.dp))
                                }
                                OutlinedButton(
                                    onClick = { player.play(a.song.shuffled(), 0); onNowPlaying() },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f))
                                ) {
                                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                                    Text("Ngẫu nhiên", modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(a.song, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song, index = index,
                    isCurrent = playState.current?.title?.toString() == song.title,
                    onClick = {
                        if (selectionMode) toggle(song.id)
                        else { player.play(a.song, index); onNowPlaying() }
                    },
                    onAdd = { pendingAdd = listOf(song.id) },
                    onDelete = if (isAdmin) { { songToDelete = song } } else null,
                    onLongPress = { toggle(song.id) },
                    selectionMode = selectionMode,
                    selected = song.id in selected,
                )
            }
        }

        if (selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Filled.Close, "Bỏ chọn", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("Đã chọn ${selected.size}", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Button(onClick = { pendingAdd = selected.toList(); selected = emptySet() }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                        Text("Thêm vào playlist", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }

    if (showDeleteAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAlbumDialog = false },
            title = { Text("Xoá Album vĩnh viễn?") },
            text = { Text("Bạn có chắc chắn muốn xoá vĩnh viễn album \"${a.name}\"? Thao tác này sẽ xoá toàn bộ tệp tin nhạc của album này trên đĩa cứng máy chủ và không thể khôi phục.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAlbumDialog = false
                        scope.launch {
                            try {
                                val success = repo.deleteAlbum(albumId)
                                if (success) {
                                    Toast.makeText(context, "Đã xoá album thành công", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, "Xoá album thất bại", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xoá vĩnh viễn")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlbumDialog = false }) {
                    Text("Huỷ")
                }
            }
        )
    }

    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("Xoá bài hát vĩnh viễn?") },
            text = { Text("Bạn có chắc chắn muốn xoá vĩnh viễn bài hát \"${song.title}\"? Thao tác này sẽ xoá tệp tin nhạc trên đĩa cứng máy chủ và không thể khôi phục.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = songToDelete
                        songToDelete = null
                        if (toDelete != null) {
                            scope.launch {
                                try {
                                    val success = repo.deleteSong(toDelete.id)
                                    if (success) {
                                        Toast.makeText(context, "Đã xoá bài hát thành công", Toast.LENGTH_SHORT).show()
                                        vm.refresh()
                                    } else {
                                        Toast.makeText(context, "Xoá bài hát thất bại", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xoá vĩnh viễn")
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text("Huỷ")
                }
            }
        )
    }

    pendingAdd?.let { ids ->
        AddToPlaylistSheet(songIds = ids, onDismiss = { pendingAdd = null })
    }
}
