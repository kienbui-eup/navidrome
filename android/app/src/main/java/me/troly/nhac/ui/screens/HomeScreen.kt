package me.troly.nhac.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.Album
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.components.AlbumRow
import me.troly.nhac.ui.components.CoverImage
import me.troly.nhac.ui.components.SkeletonHome

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val newest: List<Album> = emptyList(),
    val frequent: List<Album> = emptyList(),
    val recent: List<Album> = emptyList(),
    val random: List<Album> = emptyList(),
)

class HomeViewModel(private val repo: SubsonicRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()
    init { load() }
    fun load() = viewModelScope.launch {
        _state.value = HomeState(loading = true)
        try {
            _state.value = HomeState(
                loading = false,
                newest = repo.albums("newest", 20),
                frequent = repo.albums("frequent", 20),
                recent = repo.albums("recent", 20),
                random = repo.albums("random", 20),
            )
        } catch (e: Exception) {
            _state.value = HomeState(loading = false, error = e.message ?: "Lỗi tải")
        }
    }
}

@Composable
fun HomeScreen(onAlbum: (Album) -> Unit) {
    val repo = LocalRepo.current
    val vm: HomeViewModel = viewModel(key = "home") { HomeViewModel(repo) }
    val s by vm.state.collectAsState()

    when {
        s.loading -> SkeletonHome()
        s.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Lỗi: ${s.error}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
        }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            s.newest.firstOrNull()?.let { FeaturedHero(it, repo, onAlbum) }
            AlbumRow("Mới thêm", s.newest, onAlbum)
            AlbumRow("Nghe nhiều", s.frequent, onAlbum)
            AlbumRow("Gần đây", s.recent, onAlbum)
            AlbumRow("Ngẫu nhiên", s.random, onAlbum)
        }
    }
}

@Composable
private fun FeaturedHero(album: Album, repo: SubsonicRepository, onAlbum: (Album) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(16.dp).height(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onAlbum(album) },
    ) {
        CoverImage(
            url = repo.config.coverArtUrl(album.coverArt, 800),
            contentDescription = album.name,
            corner = 18.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.4f to Color.Transparent,
                    1f to Color(0xE60B0B0D),
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("Nổi bật", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(album.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            album.artist?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD8CFBE),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
