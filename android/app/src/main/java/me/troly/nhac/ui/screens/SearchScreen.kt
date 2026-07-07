package me.troly.nhac.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.search(it) },
            label = { Text("Tìm bài hát, album, nghệ sĩ") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (result.album.isNotEmpty()) {
                item { AlbumRow("Album", result.album, onAlbum) }
            }
            if (result.song.isNotEmpty()) {
                item {
                    Text("Bài hát", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                }
                itemsIndexed(result.song, key = { _, s -> s.id }) { index, song ->
                    SongRow(song, index, isCurrent = false, onClick = {
                        player.play(result.song, index); onNowPlaying()
                    })
                }
            }
        }
    }
}
