package me.troly.nhac.ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import me.troly.nhac.data.subsonic.isServerTranscodeSuffix
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.playback.AudioDeviceHelper
import me.troly.nhac.playback.AudioDecisionEngine
import me.troly.nhac.playback.AudioPathReport
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.LocalRecManager
import me.troly.nhac.ui.components.formatDuration

/**
 * Animated audio visualizer bars using Compose InfiniteTransition.
 */
@Composable
fun AudioVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "visualizer")
    
    val h1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.1f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "h4"
    )

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val activeColor = MaterialTheme.colorScheme.primary
        val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        
        listOf(h1, h2, h3, h4).forEach { heightVal ->
            val h = if (isPlaying) heightVal else 0.2f
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(h)
                    .background(if (isPlaying) activeColor else inactiveColor, RoundedCornerShape(1.5.dp))
            )
        }
    }
}

@Composable
fun AnimatedAmbientBackground(artworkUri: Any?, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_background")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.25f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val translationX by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translateX"
    )
    val translationY by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translateY"
    )

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                    this.rotationZ = rotation
                    this.translationX = translationX
                    this.translationY = translationY
                }
                .blur(64.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xCC0B0B0D),
                            Color(0xF80B0B0D)
                        )
                    )
                )
        )
    }
}

/**
 * Helper to check if a USB DAC or USB headset is currently attached to the system.
 */
fun isUsbDacConnected(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    return devices.any { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    // Intercept back presses to slide down player smoothly instead of exiting application
    BackHandler(onBack = onClose)

    val player = LocalPlayer.current
    val repo = LocalRepo.current
    val recManager = LocalRecManager.current
    val context = LocalContext.current

    val state by player.state.collectAsState()
    val meta = state.current
    val art = meta?.artworkUri

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) { player.tick(); delay(500) }
    }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val progress = if (scrubbing) scrubValue else state.positionMs.toFloat() / duration

    // Dynamic interactive artwork properties
    val isPlaying = state.isPlaying
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.88f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "art_scale"
    )
    val artCorner by animateDpAsState(
        targetValue = if (isPlaying) 18.dp else 26.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "art_corner"
    )
    val artShadow by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 8.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "art_shadow"
    )

    // Interactive Audio Quality Badges & Visualizer State
    var activeDevice by remember { mutableStateOf(AudioDeviceHelper.getActiveDeviceDetails(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDevice = AudioDeviceHelper.getActiveDeviceDetails(context)
            delay(1500)
        }
    }
    
    val extras = meta?.extras
    val suffix = extras?.getString("suffix")
    val bitRate = extras?.getInt("bitRate") ?: 0
    val bitDepth = extras?.getInt("bitDepth") ?: 0
    val samplingRate = extras?.getInt("samplingRate") ?: 0

    // HQPlayer filter state from global persistent player connection
    val activeFilter by player.activeFilter.collectAsState()
    val activeDither by player.activeDither.collectAsState()
    val activeModulator by player.activeModulator.collectAsState()

    // Dynamic intelligent Audio Signal Path computation via AudioDecisionEngine
    val audioReport = remember(activeDevice, suffix, bitDepth, samplingRate, bitRate, activeFilter, activeDither, activeModulator) {
        AudioDecisionEngine.determineAudioPath(
            activeDevice = activeDevice,
            sourceSuffix = suffix,
            sourceBitDepth = bitDepth,
            sourceSamplingRate = samplingRate,
            sourceBitRate = bitRate,
            selectedFilter = activeFilter,
            selectedDither = activeDither,
            selectedModulator = activeModulator
        )
    }

    val ledColor = audioReport.ledColor
    val isDspEnabled = audioReport.isUpsampled

    val (formatName, originalSpecs) = remember(suffix, bitDepth, samplingRate, bitRate) {
        formatOriginalSpecs(suffix, bitDepth, samplingRate, bitRate)
    }

    // Media3 Queue State tracking
    var queueItems by remember { mutableStateOf(emptyList<androidx.media3.common.MediaMetadata>()) }
    var currentIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(state.current) {
        queueItems = player.getQueue()
        currentIndex = player.getCurrentIndex()
    }

    // Smart Recommendations from Last.fm & Local fallback
    var recSongs by remember { mutableStateOf(emptyList<me.troly.nhac.data.subsonic.Song>()) }
    var isRecLoading by remember { mutableStateOf(false) }

    LaunchedEffect(meta) {
        if (meta != null) {
            isRecLoading = true
            val title = meta.title?.toString().orEmpty()
            val artistName = meta.artist?.toString().orEmpty()
            val artistId = meta.extras?.getString("artistId")
            recSongs = recManager.recommendSimilar(title, artistName, artistId, repo)
            isRecLoading = false
        } else {
            recSongs = emptyList()
        }
    }

    val autoRadioEnabled by recManager.autoRadioEnabled.collectAsState()

    // Responsive design detection (Folded vs. Unfolded Cover screen)
    val configuration = LocalConfiguration.current
    val isTv = false // Fallback
    val isWideScreen = !isTv && configuration.screenWidthDp >= 600

    Box(Modifier.fillMaxSize()) {
        // Ambient Fluid moving artwork background
        AnimatedAmbientBackground(artworkUri = art)

        if (isWideScreen) {
            // ── WIDESCREEN DUAL-COLUMN DASHBOARD ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // COL 1: Left panel (Artwork, Controls, Signal Path)
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Đóng",
                                tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            text = "ĐANG PHÁT (NOW PLAYING)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = art, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                }
                                .shadow(artShadow, RoundedCornerShape(artCorner))
                                .clip(RoundedCornerShape(artCorner))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        meta?.title?.toString() ?: "",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = Color.White, textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                    )
                    Text(
                        meta?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.titleMedium, color = Color(0xFFC4BBA6),
                        textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )

                    Spacer(Modifier.height(12.dp))

                    // Quality LED Badge
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x11FFFFFF))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AudioVisualizer(isPlaying = state.isPlaying, modifier = Modifier.padding(end = 12.dp))
                            
                            if (!suffix.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x22FFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = suffix.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            
                            val qualityText = remember(suffix, bitRate, bitDepth, samplingRate) {
                                if (bitDepth > 0 && samplingRate > 0) {
                                    "${bitDepth}-bit / ${samplingRate / 1000.0} kHz"
                                } else if (bitRate > 0) {
                                    "${bitRate} kbps"
                                } else {
                                    ""
                                }
                            }
                            
                            if (qualityText.isNotEmpty()) {
                                Text(
                                    text = qualityText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC4BBA6),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            
                            val outputLabel = remember(activeDevice) {
                                when {
                                    activeDevice.typeLabel == "USB DAC" -> "Direct USB DAC"
                                    activeDevice.name.contains("Buds2 Pro", ignoreCase = true) -> "Buds 2 Pro (SSC)"
                                    activeDevice.name.contains("UP5", ignoreCase = true) -> "UP5 (LDAC)"
                                    else -> activeDevice.typeLabel
                                }
                            }
                            
                            Text(
                                text = "➔ $outputLabel",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ledColor
                            )
                            
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ledColor)
                            )
                        }
                    }

                    // Seekbar
                    Slider(
                        value = progress.coerceIn(0f, 1f),
                        onValueChange = { scrubbing = true; scrubValue = it },
                        onValueChangeFinished = { player.seekTo((scrubValue * duration).toLong()); scrubbing = false },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color(0x33FFFFFF),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatMs(state.positionMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                        Text(formatMs(state.durationMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                    }

                    // Controls
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipPrevious, "Bài trước", modifier = Modifier.size(36.dp), tint = Color.White)
                        }
                        Box(
                            Modifier.padding(horizontal = 16.dp).size(64.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                .clickable { player.togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Phát/Dừng", modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipNext, "Bài sau", modifier = Modifier.size(36.dp), tint = Color.White)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Embedded Signal Path Steps inside Left Column scroll
                    Text(
                        text = "ĐƯỜNG TRUYỀN TÍN HIỆU (SIGNAL PATH)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0AFFFFFF))
                            .padding(16.dp)
                    ) {
                        SignalPathStep(
                            title = "1. NGUỒN NHẠC GỐC (ORIGINAL FILE)",
                            value = formatName,
                            subValue = originalSpecs,
                            isFirst = true,
                            color = ledColor
                        )

                        val isTranscoded = remember(suffix) { isServerTranscodeSuffix(suffix) }
                        val streamingValue = if (isTranscoded) "Server Transcoded (FLAC 24-bit)" else "Direct Stream (Nguyên bản)"
                        val streamingSub = if (isTranscoded) {
                            "Dữ liệu gốc định dạng $suffix được máy chủ tự động chuyển mã không hao tổn sang FLAC 24-bit PCM giúp Android giải mã tối ưu."
                        } else {
                            "Truyền phát trực tiếp ở chất lượng nguyên gốc từ máy chủ, không qua xử lý hay tái nén."
                        }
                        SignalPathStep(
                            title = "2. PHƯƠNG THỨC TRUYỀN PHÁT (STREAMING)",
                            value = streamingValue,
                            subValue = streamingSub,
                            color = ledColor
                        )

                        val engineValue = if (isDspEnabled) "HQPlayer-grade Audiophile DSP" else "ExoPlayer Engine (Float 32-bit)"
                        SignalPathStep(
                            title = "3. BỘ GIẢI MÃ SỐ (AUDIO ENGINE / DSP)",
                            value = engineValue,
                            subValue = audioReport.dspEngineStatus,
                            color = ledColor
                        )

                        val deviceDetails = "${activeDevice.techLabel} • Định dạng thực tế: ${audioReport.actualOutputFormat}\n${activeDevice.description}"
                        SignalPathStep(
                            title = "4. THIẾT BỊ ĐẦU RA (OUTPUT HARDWARE)",
                            value = activeDevice.name,
                            subValue = deviceDetails,
                            color = ledColor
                        )

                        SignalPathStep(
                            title = "5. DỰ BÁO CHẤT LƯỢNG (AUDIO FORECAST)",
                            value = audioReport.statusLabel,
                            subValue = audioReport.statusDesc,
                            isLast = true,
                            color = ledColor
                        )
                    }
                }

                // COL 2: Right panel (Queue, Radio Recommendations, DSP Card)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Queue Header
                    item {
                        Text(
                            text = "HÀNG ĐỢI PHÁT (ACTIVE PLAYLIST)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Queue list items
                    if (queueItems.isEmpty()) {
                        item {
                            Text("Hàng đợi trống", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    } else {
                        itemsIndexed(queueItems) { idx, item ->
                            val isPlayingCurrent = idx == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isPlayingCurrent) Color(0x18FFFFFF) else Color.Transparent)
                                    .clickable { player.skipToQueueItem(idx) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF231F27)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPlayingCurrent) {
                                        AudioVisualizer(isPlaying = state.isPlaying)
                                    } else {
                                        Text("${idx + 1}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.title?.toString() ?: "Unknown",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isPlayingCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isPlayingCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.artist?.toString() ?: "Unknown Artist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFC4BBA6),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!isPlayingCurrent) {
                                    IconButton(
                                        onClick = {
                                            player.removeQueueItem(idx)
                                            queueItems = player.getQueue()
                                            currentIndex = player.getCurrentIndex()
                                        }
                                    ) {
                                        Icon(Icons.Filled.Delete, "Xoá", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Autoplay Radio Toggle Card
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x11FFFFFF))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Nhạc Radio tự động (Autoplay)", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Tự động tìm và nối tiếp các bài hát tương tự khi hết hàng đợi", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                            }
                            Switch(
                                checked = autoRadioEnabled,
                                onCheckedChange = { recManager.setAutoRadioEnabled(it) }
                            )
                        }
                    }

                    // Smart Recommendations Header
                    item {
                        Text(
                            text = "ĐỀ XUẤT THÔNG MINH (RECOMMENDATIONS)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Recommendations List
                    if (isRecLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else if (recSongs.isEmpty()) {
                        item {
                            Text("Không có đề xuất phù hợp", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    } else {
                        itemsIndexed(recSongs) { index, song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        player.appendSong(song)
                                        queueItems = player.getQueue()
                                        currentIndex = player.getCurrentIndex()
                                        player.skipToQueueItem(player.getQueue().size - 1)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = repo.config.coverArtUrl(song.coverArt, 120),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF231F27)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        song.artist ?: "Unknown Artist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFC4BBA6),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        player.appendSong(song)
                                        queueItems = player.getQueue()
                                        currentIndex = player.getCurrentIndex()
                                        Toast.makeText(context, "Đã thêm vào cuối hàng đợi", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Filled.PlayArrow, "Thêm vào hàng đợi", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // HQPlayer DSP Controls
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x0EFFFFFF))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("BỘ LỌC CHUYÊN DỤNG (HQPLAYER DSP)", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)

                            // 1. Resampler filter
                            Column {
                                Text("1. Resampler Filter (Bộ lọc nội suy)", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Lựa chọn thuật toán tăng tần số lấy mẫu", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                                Spacer(Modifier.height(8.dp))
                                
                                val filterOptions = listOf("Bypass", "poly-sinc-xtr-lp", "Closed-Form-M", "sinc-L")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    filterOptions.forEach { option ->
                                        val isSelected = activeFilter == option
                                        val bgAnimatedColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x0EFFFFFF),
                                            animationSpec = tween(durationMillis = 200), label = "filterBg"
                                        )
                                        val textAnimatedColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFE5DECD),
                                            animationSpec = tween(durationMillis = 200), label = "filterText"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(bgAnimatedColor)
                                                .clickable { player.setFilter(option) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = option.split("-").last(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = textAnimatedColor
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Dither option
                            Column {
                                Text("2. Dither & Noise Shaper (Bộ phân dither)", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Chuyển dịch nhiễu lượng tử cơ học ra dải siêu cao tần", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                                Spacer(Modifier.height(8.dp))
                                
                                val ditherOptions = listOf("None", "Gauss dither", "NS9 (Shaper)", "LNS15")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ditherOptions.forEach { option ->
                                        val isSelected = activeDither == option
                                        val bgAnimatedColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x0EFFFFFF),
                                            animationSpec = tween(durationMillis = 200), label = "ditherBg"
                                        )
                                        val textAnimatedColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFE5DECD),
                                            animationSpec = tween(durationMillis = 200), label = "ditherText"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(bgAnimatedColor)
                                                .clickable { player.setDither(option) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = option.split(" ")[0],
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = textAnimatedColor
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Modulator Option
                            Column {
                                Text("3. Modulator / Upsampler (Bộ điều chế SDM)", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Cấu hình dồn mẫu Sigma-Delta lên dòng siêu cao tần 1-bit", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                                Spacer(Modifier.height(8.dp))
                                
                                val modulatorOptions = listOf("PCM (Bit-Perfect)", "DSD64 (Sigma-Delta)", "DSD512 (HQ-grade)")
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    modulatorOptions.forEach { option ->
                                        val isSelected = activeModulator == option
                                        val bgAnimatedColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x0EFFFFFF),
                                            animationSpec = tween(durationMillis = 200), label = "modBg"
                                        )
                                        val textAnimatedColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFE5DECD),
                                            animationSpec = tween(durationMillis = 200), label = "modText"
                                        )
                                        val indicatorColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF00E676),
                                            animationSpec = tween(durationMillis = 200), label = "modIndicator"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(bgAnimatedColor)
                                                .clickable { player.setModulator(option) }
                                                .padding(vertical = 10.dp, horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(indicatorColor)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = option,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textAnimatedColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Dynamic Hardware Specs matching note
                            val hardwareNote = when (activeDevice.typeLabel) {
                                "USB DAC" -> "💡 Lưu ý phần cứng: USB DAC Topping E30 đang được kết nối. Chip giải mã AK4493 hỗ trợ gốc DSD512. Hãy kết hợp với Pre Suca T5C bóng Mullard 403b và op-amp Muses02 để trải nghiệm âm thanh analog cực mượt, dải âm ấm dày và nhạc tính đỉnh cao!"
                                "Bluetooth" -> "💡 Lưu ý phần cứng: Đang phát qua Bluetooth không dây. Codec LDAC/SSC hỗ trợ dải động rộng nhưng không truyền tải được luồng DSD thô. Các bộ điều chế SDM tạm thời được giảm mẫu về PCM 24-bit/96kHz tối ưu."
                                else -> "💡 Lưu ý phần cứng: Đang phát ra Loa ngoài của Galaxy Z Fold 5. Để bảo vệ thời lượng pin và tránh quá nhiệt, các bộ lọc upsampling nặng được bypass. Hãy cắm USB DAC Topping E30 qua cổng Type-C để thưởng thức âm thanh Roon-grade!"
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x08FFFFFF))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = hardwareNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC4BBA6),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ── NARROW PORTRAIT UNIFIED SCROLL FEED (MOBILE COVER SCREEN) ──────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pin header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Đóng",
                                tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            text = "ĐANG PHÁT (NOW PLAYING)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Album Art
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = art, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize(0.85f)
                                .graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                }
                                .shadow(artShadow, RoundedCornerShape(artCorner))
                                .clip(RoundedCornerShape(artCorner))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }

                // Meta details
                item {
                    Text(
                        meta?.title?.toString() ?: "",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = Color.White, textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                    )
                    Text(
                        meta?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.bodyLarge, color = Color(0xFFC4BBA6),
                        textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }

                // LED interactive Quality Badge
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x11FFFFFF))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AudioVisualizer(isPlaying = state.isPlaying, modifier = Modifier.padding(end = 12.dp))
                            
                            if (!suffix.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x22FFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = suffix.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            
                            val qualityText = remember(suffix, bitRate, bitDepth, samplingRate) {
                                if (bitDepth > 0 && samplingRate > 0) {
                                    "${bitDepth}-bit / ${samplingRate / 1000.0} kHz"
                                } else if (bitRate > 0) {
                                    "${bitRate} kbps"
                                } else {
                                    ""
                                }
                            }
                            
                            if (qualityText.isNotEmpty()) {
                                Text(
                                    text = qualityText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC4BBA6),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            
                            val outputLabel = remember(activeDevice) {
                                when {
                                    activeDevice.typeLabel == "USB DAC" -> "Direct USB DAC"
                                    activeDevice.name.contains("Buds2 Pro", ignoreCase = true) -> "Buds 2 Pro (SSC)"
                                    activeDevice.name.contains("UP5", ignoreCase = true) -> "UP5 (LDAC)"
                                    else -> activeDevice.typeLabel
                                }
                            }
                            
                            Text(
                                text = "➔ $outputLabel",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ledColor
                            )
                            
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ledColor)
                            )
                        }
                    }
                }

                // Progress SeekBar
                item {
                    Column {
                        Slider(
                            value = progress.coerceIn(0f, 1f),
                            onValueChange = { scrubbing = true; scrubValue = it },
                            onValueChangeFinished = { player.seekTo((scrubValue * duration).toLong()); scrubbing = false },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color(0x33FFFFFF),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatMs(state.positionMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                            Text(formatMs(state.durationMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                        }
                    }
                }

                // Playback Buttons
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipPrevious, "Bài trước", modifier = Modifier.size(36.dp), tint = Color.White)
                        }
                        Box(
                            Modifier.padding(horizontal = 16.dp).size(64.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                .clickable { player.togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Phát/Dừng", modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipNext, "Bài sau", modifier = Modifier.size(36.dp), tint = Color.White)
                        }
                    }
                }

                // ── PORTRAIT INLINE PLAYLIST QUEUE ─────────────────────────────────────
                item {
                    Text(
                        text = "DANH SÁCH PHÁT TIẾP THEO (QUEUE)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (queueItems.isEmpty()) {
                    item {
                        Text("Hàng đợi trống", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                } else {
                    val upcomingItems = queueItems.drop(currentIndex + 1)
                    if (upcomingItems.isEmpty()) {
                        item {
                            Text("Không có bài hát tiếp theo", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    } else {
                        itemsIndexed(upcomingItems) { relativeIdx, item ->
                            val idx = currentIndex + 1 + relativeIdx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { player.skipToQueueItem(idx) }
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x18FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${relativeIdx + 1}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.title?.toString() ?: "Unknown",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.artist?.toString() ?: "Unknown Artist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFC4BBA6),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        player.removeQueueItem(idx)
                                        queueItems = player.getQueue()
                                        currentIndex = player.getCurrentIndex()
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, "Xoá", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Autoplay Radio Card in scroll feed
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x11FFFFFF))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nhạc Radio tự động (Autoplay)", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Tự động tìm phát nhạc tương tự khi hết hàng đợi", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                        }
                        Switch(
                            checked = autoRadioEnabled,
                            onCheckedChange = { recManager.setAutoRadioEnabled(it) }
                        )
                    }
                }

                // ── PORTRAIT INLINE RECOMMENDATIONS ────────────────────────────────────
                item {
                    Text(
                        text = "GỢI Ý BÀI HÁT TƯƠNG TỰ (RADIO)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (isRecLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (recSongs.isEmpty()) {
                    item {
                        Text("Không có gợi ý tương tự", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                } else {
                    itemsIndexed(recSongs) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    player.appendSong(song)
                                    queueItems = player.getQueue()
                                    currentIndex = player.getCurrentIndex()
                                    player.skipToQueueItem(player.getQueue().size - 1)
                                }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = repo.config.coverArtUrl(song.coverArt, 120),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF231F27)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                               Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist ?: "Unknown Artist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC4BBA6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    player.appendSong(song)
                                    queueItems = player.getQueue()
                                    currentIndex = player.getCurrentIndex()
                                    Toast.makeText(context, "Đã thêm vào cuối hàng đợi", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Filled.PlayArrow, "Thêm", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // ── PORTRAIT INLINE AUDIOPHILE SIGNAL PATH ──────────────────────────────
                item {
                    Text(
                        text = "ĐƯỜNG TRUYỀN TÍN HIỆU (SIGNAL PATH)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0AFFFFFF))
                            .padding(16.dp)
                    ) {
                        SignalPathStep(
                            title = "1. NGUỒN NHẠC GỐC (ORIGINAL FILE)",
                            value = formatName,
                            subValue = originalSpecs,
                            isFirst = true,
                            color = ledColor
                        )

                        val isTranscoded = remember(suffix) { isServerTranscodeSuffix(suffix) }
                        val streamingValue = if (isTranscoded) "Server Transcoded (FLAC 24-bit)" else "Direct Stream (Nguyên bản)"
                        val streamingSub = if (isTranscoded) {
                            "Dữ liệu gốc định dạng $suffix được máy chủ tự động chuyển mã không hao tổn sang FLAC 24-bit PCM giúp Android giải mã tối ưu."
                        } else {
                            "Truyền phát trực tiếp ở chất lượng nguyên gốc từ máy chủ, không qua xử lý hay tái nén."
                        }
                        SignalPathStep(
                            title = "2. PHƯƠNG THỨC TRUYỀN PHÁT (STREAMING)",
                            value = streamingValue,
                            subValue = streamingSub,
                            color = ledColor
                        )

                        val engineValue = if (isDspEnabled) "HQPlayer-grade Audiophile DSP" else "ExoPlayer Engine (Float 32-bit)"
                        SignalPathStep(
                            title = "3. BỘ GIẢI MÃ SỐ (AUDIO ENGINE / DSP)",
                            value = engineValue,
                            subValue = audioReport.dspEngineStatus,
                            color = ledColor
                        )

                        val deviceDetails = "${activeDevice.techLabel} • Định dạng thực tế: ${audioReport.actualOutputFormat}\n${activeDevice.description}"
                        SignalPathStep(
                            title = "4. THIẾT BỊ ĐẦU RA (OUTPUT HARDWARE)",
                            value = activeDevice.name,
                            subValue = deviceDetails,
                            color = ledColor
                        )

                        SignalPathStep(
                            title = "5. DỰ BÁO CHẤT LƯỢNG (AUDIO FORECAST)",
                            value = audioReport.statusLabel,
                            subValue = audioReport.statusDesc,
                            isLast = true,
                            color = ledColor
                        )
                    }
                }

                // Dynamic Hardware Specs matching note
                item {
                    val hardwareNote = when (activeDevice.typeLabel) {
                        "USB DAC" -> "💡 Lưu ý phần cứng: USB DAC Topping E30 đang được kết nối. Chip giải mã AK4493 hỗ trợ gốc DSD512. Hãy kết hợp với Pre Suca T5C bóng Mullard 403b và op-amp Muses02 để trải nghiệm âm thanh analog cực mượt, dải âm ấm dày và nhạc tính đỉnh cao!"
                        "Bluetooth" -> "💡 Lưu ý phần cứng: Đang phát qua Bluetooth không dây. Codec LDAC/SSC hỗ trợ dải động rộng nhưng không truyền tải được luồng DSD thô. Các bộ điều chế SDM tạm thời được giảm mẫu về PCM 24-bit/96kHz tối ưu."
                        else -> "💡 Lưu ý phần cứng: Đang phát ra Loa ngoài của Galaxy Z Fold 5. Để bảo vệ thời lượng pin và tránh quá nhiệt, các bộ lọc upsampling nặng được bypass. Hãy cắm USB DAC Topping E30 qua cổng Type-C để thưởng thức âm thanh Roon-grade!"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x08FFFFFF))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = hardwareNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC4BBA6),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalPathStep(
    title: String,
    value: String,
    subValue: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Line and Node column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp).padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(64.dp)
                        .background(color.copy(alpha = 0.25f))
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f).padding(bottom = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC4BBA6),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatOriginalSpecs(suffix: String?, bitDepth: Int, samplingRate: Int, bitRate: Int): Pair<String, String> {
    val fmt = suffix?.lowercase() ?: "audio"
    val isDsd = fmt in setOf("dsf", "dff", "dsd")
    
    val formatName = when (fmt) {
        "dsf", "dff", "dsd" -> "DSD (Direct Stream Digital)"
        "aif", "aiff" -> "AIFF (Audio Interchange Format)"
        "flac" -> "FLAC (Lossless Audio)"
        "mp3" -> "MP3 (MPEG Audio)"
        "m4a", "aac" -> "AAC / ALAC (MPEG-4 Audio)"
        "wav" -> "WAV (Waveform Audio)"
        else -> fmt.uppercase()
    }
    
    val resolution = when {
        isDsd -> {
            if (samplingRate >= 11289600) "DSD256 (1-bit / 11.2 MHz)"
            else if (samplingRate >= 5644800) "DSD128 (1-bit / 5.6 MHz)"
            else "DSD64 (1-bit / 2.82 MHz)"
        }
        bitDepth > 0 && samplingRate > 0 -> {
            "${bitDepth}-bit / ${samplingRate / 1000.0} kHz"
        }
        samplingRate > 0 -> {
            "${samplingRate / 1000.0} kHz"
        }
        else -> "Độ phân giải chuẩn"
    }
    
    val brString = if (bitRate > 0) "${bitRate} kbps" else ""
    val category = if (fmt in setOf("dsf", "dff", "dsd", "flac", "aif", "aiff", "wav")) "Studio Quality (Lossless)" else "Standard Quality"
    
    val details = listOfNotNull(
        resolution,
        if (brString.isNotEmpty()) brString else null,
        category
    ).joinToString(" • ")
    
    return Pair(formatName, details)
}

private fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())
