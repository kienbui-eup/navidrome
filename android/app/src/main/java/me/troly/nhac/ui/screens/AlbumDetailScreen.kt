package me.troly.nhac.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import me.troly.nhac.ui.components.CoverImage
import me.troly.nhac.ui.components.SongRow

class AlbumViewModel(private val repo: SubsonicRepository, private val albumId: String) : ViewModel() {
    private val _album = MutableStateFlow<Album?>(null)
    val album = _album.asStateFlow()
    init { viewModelScope.launch { runCatching { _album.value = repo.album(albumId) } } }
}

@Composable
fun AlbumDetailScreen(albumId: String, onBack: () -> Unit, onNowPlaying: () -> Unit) {
    BackHandler(onBack = onBack)
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val vm: AlbumViewModel = viewModel(key = "album-$albumId") { AlbumViewModel(repo, albumId) }
    val album by vm.album.collectAsState()
    val playState by player.state.collectAsState()

    if (album == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val a = album!!
    val coverUrl = repo.config.coverArtUrl(a.coverArt, 600)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth()) {
                // Blurred backdrop
                AsyncImage(
                    model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(40.dp),
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color(0x990B0B0D), Color(0xF20B0B0D))),
                    ),
                )
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = Color.White)
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CoverImage(
                            url = coverUrl, contentDescription = a.name, corner = 16.dp,
                            modifier = Modifier.size(216.dp).shadow(20.dp, RoundedCornerShape(16.dp)),
                        )
                        Text(a.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            color = Color.White, modifier = Modifier.padding(top = 14.dp))
                        Text(listOfNotNull(a.artist, a.year?.toString(),
                            a.songCount?.let { "$it bài" }).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC4BBA6))
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
                            OutlinedButton(onClick = { player.play(a.song.shuffled(), 0); onNowPlaying() }) {
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
                onClick = { player.play(a.song, index); onNowPlaying() },
            )
        }
    }
}
