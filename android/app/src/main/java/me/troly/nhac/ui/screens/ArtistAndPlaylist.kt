package me.troly.nhac.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import me.troly.nhac.data.subsonic.Artist
import me.troly.nhac.data.subsonic.Playlist
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.LocalIsTv
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.rememberInteractionSource
import me.troly.nhac.ui.tvFocusable
import me.troly.nhac.ui.components.AddToPlaylistSheet
import me.troly.nhac.ui.components.DeletePlaylistDialog
import me.troly.nhac.ui.components.SongRow

// ── Artist detail ───────────────────────────────────────────────────────────
class ArtistViewModel(private val repo: SubsonicRepository, id: String) : ViewModel() {
    private val _artist = MutableStateFlow<Artist?>(null)
    val artist = _artist.asStateFlow()
    init { viewModelScope.launch { runCatching { _artist.value = repo.artist(id) } } }
}

@Composable
fun ArtistDetailScreen(artistId: String, onBack: () -> Unit, onAlbum: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val repo = LocalRepo.current
    val isTv = LocalIsTv.current
    val vm: ArtistViewModel = viewModel(key = "artist-$artistId") { ArtistViewModel(repo, artistId) }
    val artist by vm.artist.collectAsState()
    if (artist == null) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return }
    val a = artist!!

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 180.dp else 150.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp))
                    }
                }
                Text(a.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
            }
        }
        gridItems(a.album, key = { it.id }) { album ->
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
                album.year?.let {
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Playlists list ──────────────────────────────────────────────────────────
class PlaylistsViewModel(private val repo: SubsonicRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<Playlist>>(emptyList())
    val items = _items.asStateFlow()
    init { reload() }
    fun reload() { viewModelScope.launch { runCatching { _items.value = repo.playlists() } } }
}

@Composable
fun PlaylistsScreen(onPlaylist: (String) -> Unit) {
    val repo = LocalRepo.current
    val isTv = LocalIsTv.current
    val vm: PlaylistsViewModel = viewModel(key = "playlists") { PlaylistsViewModel(repo) }
    val items by vm.items.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Playlist?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { pl ->
            val source = rememberInteractionSource()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .then(if (isTv) Modifier.tvFocusable(source) else Modifier)
                    .clickable(interactionSource = source, indication = null) { onPlaylist(pl.id) }
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
            ) {
                AsyncImage(
                    model = repo.config.coverArtUrl(pl.coverArt, 200),
                    contentDescription = pl.name,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(pl.name, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pl.songCount ?: 0} bài", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { toDelete = pl }) {
                    Icon(Icons.Filled.DeleteOutline, "Xoá playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (ok) vm.reload()
                }
            },
        )
    }
}

// ── Playlist detail ─────────────────────────────────────────────────────────
class PlaylistViewModel(private val repo: SubsonicRepository, id: String) : ViewModel() {
    private val _pl = MutableStateFlow<Playlist?>(null)
    val pl = _pl.asStateFlow()
    init { viewModelScope.launch { runCatching { _pl.value = repo.playlist(id) } } }
}

@Composable
fun PlaylistDetailScreen(playlistId: String, onBack: () -> Unit, onNowPlaying: () -> Unit) {
    BackHandler(onBack = onBack)
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val vm: PlaylistViewModel = viewModel(key = "pl-$playlistId") { PlaylistViewModel(repo, playlistId) }
    val pl by vm.pl.collectAsState()
    if (pl == null) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return }
    val p = pl!!
    var pendingAdd by remember { mutableStateOf<List<String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp))
                }
                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, "Xoá playlist",
                        tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp))
                }
            }
            Button(
                onClick = { player.play(p.entry, 0); onNowPlaying() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Phát tất cả", modifier = Modifier.padding(start = 4.dp))
            }
        }
        itemsIndexed(p.entry, key = { _, s -> s.id }) { index, song ->
            SongRow(song, index, isCurrent = false,
                onClick = { player.play(p.entry, index); onNowPlaying() },
                onAdd = { pendingAdd = listOf(song.id) })
        }
    }

    pendingAdd?.let { ids ->
        AddToPlaylistSheet(songIds = ids, onDismiss = { pendingAdd = null })
    }

    if (confirmDelete) {
        DeletePlaylistDialog(
            name = p.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    val ok = runCatching { repo.deletePlaylist(playlistId) }.isSuccess
                    Toast.makeText(context,
                        if (ok) "Đã xoá playlist" else "Không xoá được, thử lại",
                        Toast.LENGTH_SHORT).show()
                    if (ok) onBack()
                }
            },
        )
    }
}
