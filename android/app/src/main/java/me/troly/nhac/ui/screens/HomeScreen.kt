package me.troly.nhac.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.troly.nhac.playback.AudioDeviceHelper
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.border

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
fun HomeScreen(
    onAlbum: (Album) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = LocalRepo.current
    val vm: HomeViewModel = viewModel(key = "home") { HomeViewModel(repo) }
    val s by vm.state.collectAsState()

    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Chào buổi sáng 🌅"
            in 12..17 -> "Chào buổi chiều ☀️"
            else -> "Chào buổi tối 🌃"
        }
    }

    var activeDevice by remember { mutableStateOf(AudioDeviceHelper.getActiveDeviceDetails(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDevice = AudioDeviceHelper.getActiveDeviceDetails(context)
            delay(2000)
        }
    }

    val ledColor = remember(activeDevice) {
        if (activeDevice.isLossless) Color(0xFF00E676) else if (activeDevice.isHiResCapable) Color(0xFF29B6F6) else Color(0xFFFFB300)
    }

    val capsuleSource = remember { MutableInteractionSource() }
    val capsulePressed by capsuleSource.collectIsPressedAsState()
    val capsuleScale by animateFloatAsState(if (capsulePressed) 0.96f else 1f, label = "capsule_press")

    when {
        s.loading -> SkeletonHome()
        s.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Lỗi: ${s.error}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
        }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            // Personalized Greeting & Audio Insights Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                
                // Audio Output Insights Capsule (Glossy Glassmorphic with dynamic border)
                Row(
                    modifier = Modifier
                        .scale(capsuleScale)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF222026))
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(interactionSource = capsuleSource, indication = null) { onNavigateToSettings() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Hearing,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Đầu ra: ${activeDevice.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC4BBA6),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ledColor)
                    )
                }
            }

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
    val heroSource = remember { MutableInteractionSource() }
    val heroPressed by heroSource.collectIsPressedAsState()
    val heroScale by animateFloatAsState(if (heroPressed) 0.97f else 1f, label = "hero_press")

    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(230.dp)
            .scale(heroScale)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(interactionSource = heroSource, indication = null) { onAlbum(album) },
    ) {
        CoverImage(
            url = repo.config.coverArtUrl(album.coverArt, 800),
            contentDescription = album.name,
            corner = 20.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.3f to Color.Transparent,
                    1f to Color(0xAA08070A),
                ),
            ),
        )
        // Floating glassmorphic card for metadata
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xD9141217))
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    "SẢN PHẨM NỔI BẬT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                album.artist?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC4BBA6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
