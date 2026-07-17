package me.troly.nhac.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.Album
import me.troly.nhac.data.subsonic.SearchResult3
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.components.AddToPlaylistSheet
import me.troly.nhac.ui.components.AlbumRow
import me.troly.nhac.ui.components.SongRow

class SearchViewModel(private val repo: SubsonicRepository) : ViewModel() {
    private val _result = MutableStateFlow(SearchResult3())
    val result = _result.asStateFlow()
    fun search(q: String) = viewModelScope.launch {
        if (q.isBlank()) { _result.value = SearchResult3(); return@launch }
        runCatching { repo.search(q) }.onSuccess { _result.value = it }
    }
}

@Composable
fun SearchScreen(onAlbum: (Album) -> Unit, onNowPlaying: () -> Unit) {
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val vm: SearchViewModel = viewModel(key = "search") { SearchViewModel(repo) }
    val result by vm.result.collectAsState()
    var query by remember { mutableStateOf("") }
    var pendingAdd by remember { mutableStateOf<List<String>?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.search(it) },
            placeholder = { Text("Tìm bài hát, album, nghệ sĩ...", color = Color(0x66C4BBA6)) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFFC4BBA6)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; vm.search("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Xóa",
                            tint = Color(0x88C4BBA6)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0x88C4BBA6),
                unfocusedBorderColor = Color(0x15C4BBA6),
                focusedContainerColor = Color(0xFF141318),
                unfocusedContainerColor = Color(0xFF0E0D12),
                cursorColor = Color(0xFFC4BBA6)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )

        LazyColumn(Modifier.fillMaxSize()) {
            if (result.album.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .background(Color(0xFFFFB300), RoundedCornerShape(1.5.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ALBUM PHÙ HỢP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    AlbumRow("", result.album, onAlbum)
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (result.song.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .background(Color(0xFFFFB300), RoundedCornerShape(1.5.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "BÀI HÁT TÌM THẤY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                itemsIndexed(result.song, key = { _, s -> s.id }) { index, song ->
                    SongRow(song, index, isCurrent = false,
                        onClick = { player.play(result.song, index); onNowPlaying() },
                        onAdd = { pendingAdd = listOf(song.id) })
                }
            }
        }
    }

    pendingAdd?.let { ids ->
        AddToPlaylistSheet(songIds = ids, onDismiss = { pendingAdd = null })
    }
}
