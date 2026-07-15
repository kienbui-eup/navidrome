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

    val isUsbDac = activeDevice.name.contains("USB", ignoreCase = true) || activeDevice.name.contains("DAC", ignoreCase = true)
    val isBitPerfectActive = bitPerfectEnabled && isUsbDac

    val pathColor = remember(isBitPerfectActive, aaudioEnabled) {
        when {
            isBitPerfectActive -> Color(0xFFFFB300) // Audiophile Gold (USB Exclusive Bit-Perfect)
            aaudioEnabled -> Color(0xFF00E676)      // Studio Green (AAudio Low-Latency Engine)
            else -> Color(0xFF29B6F6)               // Android Blue (Standard Mixer)
        }
    }

    val pathLabel = remember(isBitPerfectActive, aaudioEnabled) {
        when {
            isBitPerfectActive -> "USB Exclusive Bit-Perfect"
            aaudioEnabled -> "AAudio Low-Latency Engine"
            else -> "Android Audio Mixer"
        }
    }

    val pathDesc = remember(isBitPerfectActive, aaudioEnabled) {
        when {
            isBitPerfectActive -> "Bypass hệ điều hành • Tần số lấy mẫu gốc nguyên bản tới DAC"
            aaudioEnabled -> "Luồng âm thanh độ trễ thấp • Kích hoạt chế độ xử lý AAudio"
            else -> "Hỗ trợ chia sẻ âm thanh hệ thống • Tự động lấy mẫu lại 48kHz"
        }
    }

    val capsuleSource = remember { MutableInteractionSource() }
    val capsulePressed by capsuleSource.collectIsPressedAsState()
    val capsuleScale by animateFloatAsState(if (capsulePressed) 0.98f else 1f, label = "capsule_press")

    when {
        s.loading -> SkeletonHome()
        s.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Lỗi: ${s.error}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
        }
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            
            // McIntosh-style Audiophile System Monitor Dashboard
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Greeting & Sync section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = greeting.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC4BBA6), // Premium champagne gold tint
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Hệ Thống Thẩm Âm",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Manual library scan / sync
                    val refreshScope = rememberCoroutineScope()
                    var isRefreshedClicked by remember { mutableStateOf(false) }
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (isRefreshedClicked || s.isScanning) 360f else 0f,
                        animationSpec = if (s.isScanning) {
                            infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing)
                            )
                        } else {
                            tween(500)
                        },
                        label = "sync_rotation"
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.isScanning) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1500E676))
                                    .border(0.5.dp, Color(0x5500E676), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Đang đồng bộ...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x12FFFFFF))
                                .clickable {
                                    isRefreshedClicked = true
                                    refreshScope.launch {
                                        repo.triggerLocalRefresh()
                                        delay(500)
                                        isRefreshedClicked = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = "Đồng bộ",
                                tint = if (s.isScanning) Color(0xFF00E676) else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer(rotationZ = rotationAngle)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Premium McIntosh-inspired DAC Front Panel Screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(capsuleScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF141318), Color(0xFF0B0A0E))
                            )
                        )
                        .border(
                            width = 0.5.dp,
                            color = Color(0x25C4BBA6), // Elegant gold-tinted hairline border
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(interactionSource = capsuleSource, indication = null) {
                            onNavigateToSettings()
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header bar inside the console
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(pathColor)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "SYSTEM MONITOR • ACTIVE AUDIO PATH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0x88C4BBA6),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(pathColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isBitPerfectActive) "CLASS-A" else "HI-FI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    color = pathColor,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Large Display Window (simulating a vacuum tube or VFD glass screen)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF09080B))
                                .border(0.5.dp, Color(0xFF18161D), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "THIẾT BỊ ĐẦU RA",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = Color(0xFF8B8172),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = activeDevice.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = pathLabel.uppercase(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = pathColor,
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "ĐƯỜNG TRUYỀN",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        color = Color(0xFF8B8172),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isBitPerfectActive) "DIRECT" else "INTERNAL",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isBitPerfectActive) Color(0xFFFFB300) else Color(0xFFCCCCCC)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (isBitPerfectActive) "Pure Bit-Perfect" else "Android Shared Mixer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF8B8172),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = pathDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B8172),
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Thiết lập ➜",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC4BBA6)
                            )
                        }
                    }
                }
            }

            s.newest.firstOrNull()?.let { FeaturedHero(it, repo, onAlbum) }
            
            AudiophileAlbumRow(
                title = "Bản ghi mới thêm",
                subtitle = "RECENT ACQUISITIONS • Tác phẩm mới cập nhật vào bộ sưu tập",
                albums = s.newest,
                repo = repo,
                onAlbum = onAlbum
            )
            
            AudiophileAlbumRow(
                title = "Tác phẩm thẩm âm nhiều",
                subtitle = "HIGH ROTATION ARCHIVE • Các ấn phẩm có tần suất thưởng thức cao nhất",
                albums = s.frequent,
                repo = repo,
                onAlbum = onAlbum
            )
            
            AudiophileAlbumRow(
                title = "Lịch sử thẩm âm",
                subtitle = "RECENT SESSION LOG • Nhật ký các album được nghe gần đây",
                albums = s.recent,
                repo = repo,
                onAlbum = onAlbum
            )
            
            AudiophileAlbumRow(
                title = "Gợi ý khuyên dùng",
                subtitle = "CURATED REFERENCE TRACKS • Đề xuất ngẫu nhiên từ thư viện hifi",
                albums = s.random,
                repo = repo,
                onAlbum = onAlbum
            )
        }
    }
}

@Composable
private fun FeaturedHero(album: Album, repo: SubsonicRepository, onAlbum: (Album) -> Unit) {
    val heroSource = remember { MutableInteractionSource() }
    val heroPressed by heroSource.collectIsPressedAsState()
    val heroScale by animateFloatAsState(if (heroPressed) 0.97f else 1f, label = "hero_press")

    val format = remember(album) { getAlbumQualityFormat(album) }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(230.dp)
            .scale(heroScale)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 0.5.dp,
                color = Color(0x33C4BBA6), // Premium gold-tinted hairline border
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
                    0.2f to Color.Transparent,
                    1f to Color(0xFF070609),
                ),
            ),
        )

        // Reference / Master tape header label on top-left of hero image
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xE6141217))
                .border(0.5.dp, Color(0x33C4BBA6), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "REFERENCE AUDIOPHILE STANDARD",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC4BBA6),
                    letterSpacing = 1.sp
                )
            }
        }

        // Quality badge overlay on top-right of hero image
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(format.color.copy(alpha = 0.15f))
                .border(0.5.dp, format.color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (format == AlbumQualityFormat.PCM) "STUDIO MASTER" else format.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (format == AlbumQualityFormat.PCM) Color(0xFFFFB300) else format.color,
                letterSpacing = 0.5.sp
            )
        }

        // Floating glassmorphic card for metadata
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xEB110F14))
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    "TÁC PHẨM ĐỀ XUẤT (STUDIO REEL SELECTION)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC4BBA6),
                    letterSpacing = 0.5.sp
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
                        color = Color(0xFF8B8172),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
