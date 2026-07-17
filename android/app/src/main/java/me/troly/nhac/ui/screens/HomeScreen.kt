package me.troly.nhac.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.Album
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.playback.AudioDeviceHelper
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.components.AlbumCard
import me.troly.nhac.ui.components.AlbumQualityFormat
import me.troly.nhac.ui.components.getAlbumQualityFormat
import me.troly.nhac.ui.components.CoverImage
import me.troly.nhac.ui.components.SkeletonHome

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val newest: List<Album> = emptyList(),
    val frequent: List<Album> = emptyList(),
    val recent: List<Album> = emptyList(),
    val random: List<Album> = emptyList(),
    val isScanning: Boolean = false,
    val scanCount: Int? = null,
)

class HomeViewModel(private val repo: SubsonicRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init {
        load()
        
        // Listen to repo's refreshEvent to trigger silent updates
        viewModelScope.launch {
            repo.refreshEvent.collect {
                loadSilent()
            }
        }

        // Poll library scan status
        viewModelScope.launch {
            var wasScanning = false
            while (true) {
                try {
                    val status = repo.getScanStatus()
                    _state.value = _state.value.copy(
                        isScanning = status.scanning,
                        scanCount = status.count
                    )
                    // If scanning just finished, trigger a global refresh to update all lists
                    if (wasScanning && !status.scanning) {
                        repo.triggerLocalRefresh()
                    }
                    wasScanning = status.scanning
                } catch (e: Exception) {
                    // Ignore transient network errors during polling
                }
                delay(5000)
            }
        }
    }

    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        loadInternal()
    }

    private suspend fun loadInternal() {
        try {
            _state.value = _state.value.copy(
                loading = false,
                error = null,
                newest = repo.albums("newest", 20),
                frequent = repo.albums("frequent", 20),
                recent = repo.albums("recent", 20),
                random = repo.albums("random", 20),
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = e.message ?: "Lỗi tải")
        }
    }

    fun loadSilent() = viewModelScope.launch {
        try {
            val newest = repo.albums("newest", 20)
            val frequent = repo.albums("frequent", 20)
            val recent = repo.albums("recent", 20)
            val random = repo.albums("random", 20)
            _state.value = _state.value.copy(
                newest = newest,
                frequent = frequent,
                recent = recent,
                random = random,
                error = null
            )
        } catch (e: Exception) {
            // Silence background reload errors if we already have data
        }
    }
}

@Composable
fun HomeScreen(
    onAlbum: (Album) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val vm: HomeViewModel = viewModel(key = "home") { HomeViewModel(repo) }
    val s by vm.state.collectAsState()

    val bitPerfectEnabled by player.bitPerfectEnabled.collectAsState()
    val aaudioEnabled by player.aaudioEnabled.collectAsState()

    when {
        s.loading -> SkeletonHome()
        s.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Lỗi: ${s.error}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
        }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "HỆ THỐNG THẨM ÂM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC4BBA6), // Premium champagne gold tint
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Vi2Play",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    if (s.isScanning) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x1500E676))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "ĐANG ĐỒNG BỘ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }

            AudiophileAlbumRow(
                title = "Bản ghi mới thêm",
                subtitle = "RECENT ACQUISITIONS • Album mới cập nhật",
                albums = s.newest,
                repo = repo,
                onAlbum = onAlbum
            )
            
            AudiophileAlbumRow(
                title = "Tác phẩm thẩm âm nhiều",
                subtitle = "HIGH ROTATION ARCHIVE • Nghe nhiều nhất",
                albums = s.frequent,
                repo = repo,
                onAlbum = onAlbum
            )
            
            AudiophileAlbumRow(
                title = "Lịch sử thẩm âm",
                subtitle = "RECENT SESSION LOG • Nghe gần đây",
                albums = s.recent,
                repo = repo,
                onAlbum = onAlbum
            )
            
            AudiophileAlbumRow(
                title = "Gợi ý khuyên dùng",
                subtitle = "CURATED REFERENCE • Đề xuất chất lượng cao",
                albums = s.random,
                repo = repo,
                onAlbum = onAlbum
            )
        }
    }
}



/** A beautifully structured premium row layout for audiophiles. */
@Composable
private fun AudiophileAlbumRow(
    title: String,
    subtitle: String,
    albums: List<Album>,
    repo: SubsonicRepository,
    onAlbum: (Album) -> Unit
) {
    if (albums.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
    ) {
        // Shelf separator line with champagne fading gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0x33C4BBA6), Color(0x05C4BBA6))
                    )
                )
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Shelf header with elegant vertical golden marker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(Color(0xFFFFB300), RoundedCornerShape(1.5.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B8172),
                    fontSize = 9.sp
                )
            }
        }
        
        Spacer(Modifier.height(14.dp))
        
        // Shelf items
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album, repo) { onAlbum(album) }
            }
        }
    }
}
