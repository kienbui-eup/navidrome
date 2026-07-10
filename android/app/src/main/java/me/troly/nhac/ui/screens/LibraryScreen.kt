package me.troly.nhac.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.Album
import me.troly.nhac.data.subsonic.Artist
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.LocalIsTv
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.rememberInteractionSource
import me.troly.nhac.ui.tvFocusable

class LibraryViewModel(private val repo: SubsonicRepository) : ViewModel() {
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums = _albums.asStateFlow()
    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists = _artists.asStateFlow()
    
    init {
        load()
        viewModelScope.launch {
            repo.refreshEvent.collect {
                load()
            }
        }
    }
    
    private fun load() {
        viewModelScope.launch { runCatching { _albums.value = repo.albums("alphabeticalByName", 100) } }
        viewModelScope.launch { runCatching { _artists.value = repo.artists() } }
    }
}

@Composable
fun LibraryScreen(onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit) {
    val repo = LocalRepo.current
    val vm: LibraryViewModel = viewModel(key = "library") { LibraryViewModel(repo) }
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Album", "Nghệ sĩ", "Playlist")

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            titles.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i },
                    text = { Text(t, color = MaterialTheme.colorScheme.onBackground) })
            }
        }
        when (tab) {
            0 -> AlbumsGrid(repo, vm.albums.collectAsState().value, onAlbum)
            1 -> ArtistsList(repo, vm.artists.collectAsState().value, onArtist)
            else -> PlaylistsScreen(onPlaylist)
        }
    }
}

@Composable
private fun AlbumsGrid(repo: SubsonicRepository, albums: List<Album>, onAlbum: (String) -> Unit) {
    val isTv = LocalIsTv.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 180.dp else 150.dp),
        contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(albums, key = { it.id }) { album ->
            val source = rememberInteractionSource()
            Column(
                Modifier.padding(6.dp)
                    .then(if (isTv) Modifier.tvFocusable(source) else Modifier)
                    .clickable(interactionSource = source, indication = null) { onAlbum(album.id) },
            ) {
                AsyncImage(
                    model = repo.config.coverArtUrl(album.coverArt, 400),
                    contentDescription = album.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                )
                Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp))
                album.artist?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ArtistsList(repo: SubsonicRepository, artists: List<Artist>, onArtist: (String) -> Unit) {
    val isTv = LocalIsTv.current
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.id }) { artist ->
            val source = rememberInteractionSource()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .then(if (isTv) Modifier.tvFocusable(source) else Modifier)
                    .clickable(interactionSource = source, indication = null) { onArtist(artist.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                if (artist.coverArt != null) {
                    AsyncImage(
                        model = repo.config.coverArtUrl(artist.coverArt, 200),
                        contentDescription = artist.name,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp))
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(artist.name, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    artist.albumCount?.let {
                        Text("$it album", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
