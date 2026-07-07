package me.troly.nhac.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.tv.material3.Carousel
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.troly.nhac.data.subsonic.Album
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.screens.HomeViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHome(onAlbum: (String) -> Unit) {
    val repo = LocalRepo.current
    val vm: HomeViewModel = viewModel(key = "tv-home") { HomeViewModel(repo) }
    val s by vm.state.collectAsState()
    val heroes = s.newest.take(6)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        if (heroes.isNotEmpty()) {
            Carousel(
                itemCount = heroes.size,
                modifier = Modifier.fillMaxWidth().height(340.dp),
            ) { index ->
                val a = heroes[index]
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = repo.config.coverArtUrl(a.coverArt, 800),
                        contentDescription = a.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
                        ),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(40.dp)) {
                        Text("Mới thêm", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text(a.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        a.artist?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        TvShelf("Nghe nhiều", s.frequent, onAlbum)
        TvShelf("Gần đây", s.recent, onAlbum)
        TvShelf("Ngẫu nhiên", s.random, onAlbum)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvShelf(title: String, albums: List<Album>, onAlbum: (String) -> Unit) {
    val repo = LocalRepo.current
    if (albums.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 40.dp, bottom = 12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                Card(
                    onClick = { onAlbum(album.id) },
                    modifier = Modifier.width(200.dp),
                ) {
                    Column {
                        AsyncImage(
                            model = repo.config.coverArtUrl(album.coverArt, 400),
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                        )
                        Text(album.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}
