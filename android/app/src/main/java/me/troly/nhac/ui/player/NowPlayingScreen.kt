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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.isServerTranscodeSuffix
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.playback.AudioDeviceHelper
import me.troly.nhac.playback.AudioDecisionEngine
import me.troly.nhac.playback.AudioPathReport
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.LocalRecManager
import me.troly.nhac.ui.LocalIsTv
import me.troly.nhac.ui.tvFocusable
import me.troly.nhac.ui.components.swipeGestures
import me.troly.nhac.ui.rememberInteractionSource
import me.troly.nhac.ui.components.formatDuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp


/**
 * High-Fidelity 16-Band FFT Real-Time Spectrum Analyzer with spring dynamics.
 * Smoothly falls back to procedural ambient waves if direct USB bypass mode is active.
 */
@Composable
fun AudioVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val realAmplitudes by me.troly.nhac.playback.AudioVisualizerHelper.amplitudes.collectAsState()
    
    // Create gorgeous procedural bouncing heights for fallback / silent DAC output
    val transition = rememberInfiniteTransition(label = "visualizer_procedural")
    val proceduralHeights = remember {
        (0 until 16).map { i ->
            val duration = 280 + (i * 37) % 320
            val target = 0.55f + (i % 3) * 0.14f
            duration to target
        }
    }.mapIndexed { i, (duration, target) ->
        transition.animateFloat(
            initialValue = 0.15f,
            targetValue = target,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "p_h_$i"
        )
    }

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val activeColor = MaterialTheme.colorScheme.primary
        val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        
        // Check if any native amplitude values are actively pulsing (non-flat)
        val isNativeActive = realAmplitudes.any { it > 0.12f }

        for (i in 0 until 16) {
            val rawHeight = if (isNativeActive) {
                realAmplitudes.getOrElse(i) { 0.1f }
            } else {
                proceduralHeights[i].value
            }

            // Animate height changes with gentle physics-based springs
            val animatedHeight by animateFloatAsState(
                targetValue = if (isPlaying) rawHeight else 0.1f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 250f
                ),
                label = "bar_h_$i"
            )

            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(animatedHeight)
                    .background(
                        color = if (isPlaying) activeColor else inactiveColor,
                        shape = RoundedCornerShape(1.5.dp)
                    )
            )
        }
    }
}

fun Any?.toLowResArtwork(): Any? {
    if (this == null) return null
    if (this is String && this.contains("getCoverArt.view")) {
        return this.replace(Regex("([&?])size=\\d+"), "$1size=24")
    }
    if (this is android.net.Uri) {
        val uriStr = this.toString()
        if (uriStr.contains("getCoverArt.view")) {
            return android.net.Uri.parse(uriStr.replace(Regex("([&?])size=\\d+"), "$1size=24"))
        }
    }
    return this
}

@Composable
fun AnimatedAmbientBackground(artworkUri: Any?, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    
    // Slow, luxurious breathing pulse for ambient color depth
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1.15f,
        targetValue = 1.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scalePulse"
    )

    val lowResArt = remember(artworkUri) { artworkUri.toLowResArtwork() }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        if (lowResArt != null) {
            AsyncImage(
                model = lowResArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.scaleX = scalePulse
                        this.scaleY = scalePulse
                        this.alpha = pulseAlpha
                    }
                    .blur(24.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
            )
        }

        // Multi-layer high-end scrims for deep, professional contrast
        // Layer 1: Radial vignette to center the focus and darken edges
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0xE00B0B0D)),
                        radius = 1200f
                    )
                )
        )
        // Layer 2: Solid vertical dark fade for maximum legibility of controls
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x700B0B0D),
                            Color(0xF00B0B0D)
                        )
                    )
                )
        )
    }
}

/**
 * A professional, high-end Waveform Seeker that provides premium tactile scrubbing.
 * On TV, it automatically hooks into D-pad Left/Right keys to allow precise step seeking.
 */
@Composable
fun WaveformSeekbar(
    songId: String,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color(0x26FFFFFF),
    barCount: Int = 75,
    barWidth: Dp = 3.dp,
    gap: Dp = 2.dp
) {
    val isTv = LocalIsTv.current
    val source = rememberInteractionSource()
    
    // Seeded amplitudes (values between 0.12f and 1.0f)
    val heights = remember(songId, barCount) {
        val seed = songId.hashCode().toLong()
        val random = java.util.Random(seed)
        FloatArray(barCount) {
            0.12f + random.nextFloat() * 0.88f
        }
    }

    var draggingProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = draggingProgress ?: progress

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (isTv) Modifier.tvFocusable(source) else Modifier)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            val newProg = (progress - 0.05f).coerceIn(0f, 1f)
                            onSeek(newProg)
                            true
                        }
                        Key.DirectionRight -> {
                            val newProg = (progress + 0.05f).coerceIn(0f, 1f)
                            onSeek(newProg)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(songId) {
                detectTapGestures(
                    onPress = { offset ->
                        val widthPx = size.width.toFloat()
                        if (widthPx > 0) {
                            val newProg = (offset.x / widthPx).coerceIn(0f, 1f)
                            draggingProgress = newProg
                            try {
                                val success = tryAwaitRelease()
                                if (success) {
                                    onSeek(newProg)
                                }
                            } finally {
                                draggingProgress = null
                            }
                        }
                    }
                )
            }
            .pointerInput(songId) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val widthPx = size.width.toFloat()
                        if (widthPx > 0) {
                            draggingProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        draggingProgress?.let { onSeek(it) }
                        draggingProgress = null
                    },
                    onDragCancel = {
                        draggingProgress = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val widthPx = size.width.toFloat()
                        if (widthPx > 0 && draggingProgress != null) {
                            val dragChange = dragAmount.x / widthPx
                            draggingProgress = (draggingProgress!! + dragChange).coerceIn(0f, 1f)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalGapsWidth = gap.toPx() * (barCount - 1)
            val availableWidth = size.width - totalGapsWidth
            val calculatedBarWidth = availableWidth / barCount
            
            for (i in 0 until barCount) {
                val x = i * (calculatedBarWidth + gap.toPx())
                val barHeight = heights[i] * size.height
                val y = (size.height - barHeight) / 2f
                
                // Active versus inactive bars coloring
                val isPlayed = (i.toFloat() / barCount) <= displayProgress
                val color = if (isPlayed) activeColor else inactiveColor
                
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(calculatedBarWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(calculatedBarWidth / 2f)
                )
            }
        }
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
    val isTv = LocalIsTv.current

    val state by player.state.collectAsState()
    val meta = state.current
    val art = meta?.artworkUri

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) { player.tick(); delay(500) }
    }
    
    var isScreensaverActive by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                delay(1000)
                if (System.currentTimeMillis() - lastInteractionTime > 30000) {
                    if (isTv) {
                        isScreensaverActive = true
                    }
                }
            }
        } else {
            isScreensaverActive = false
        }
    }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val progress = if (scrubbing) scrubValue else state.positionMs.toFloat() / duration

    // Infinite transition for the Play/Pause Breathing Halo
    val breathingTransition = rememberInfiniteTransition(label = "play_breathing")
    val haloScale by breathingTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )
    val haloAlpha by breathingTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

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
    val currentSongId = remember(meta) { extras?.getString("songId") }
    val suffix = extras?.getString("suffix")
    val bitRate = extras?.getInt("bitRate") ?: 0
    val bitDepth = extras?.getInt("bitDepth") ?: 0
    val samplingRate = extras?.getInt("samplingRate") ?: 0

    val scope = rememberCoroutineScope()
    var songDetails by remember { mutableStateOf<me.troly.nhac.data.subsonic.Song?>(null) }
    LaunchedEffect(currentSongId) {
        val id = currentSongId ?: return@LaunchedEffect
        songDetails = null
        try {
            songDetails = repo.song(id)
        } catch (e: Exception) {
            val ex = meta?.extras
            songDetails = me.troly.nhac.data.subsonic.Song(
                id = id,
                title = meta?.title?.toString() ?: "",
                starred = ex?.getString("starred"),
                userRating = ex?.getInt("userRating")
            )
        }
    }

    val sharedPrefs = remember(context) { context.getSharedPreferences("audiophile_reviews", Context.MODE_PRIVATE) }
    var reviewNote by remember(currentSongId) {
        mutableStateOf(sharedPrefs.getString(currentSongId ?: "", "") ?: "")
    }

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
    val isWideScreen = isTv || configuration.screenWidthDp >= 600

    val focusRequester = remember { FocusRequester() }
    if (isTv) {
        LaunchedEffect(Unit) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                lastInteractionTime = System.currentTimeMillis()
                if (isScreensaverActive) {
                    isScreensaverActive = false
                    true
                } else {
                    false
                }
            }
    ) {
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
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        if (isTv) {
                            val ssSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            IconButton(
                                onClick = { isScreensaverActive = true },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .tvFocusable(ssSource)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Radio, // using Radio as standard TV-like visual, or we can use another available icon
                                    contentDescription = "Màn hình chờ",
                                    tint = Color(0xFFC4BBA6),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .swipeGestures(
                                onSwipeDown = onClose,
                                onSwipeLeft = { player.next() },
                                onSwipeRight = { player.previous() }
                            ),
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
                                .shadow(
                                    elevation = artShadow,
                                    shape = RoundedCornerShape(artCorner),
                                    clip = false,
                                    ambientColor = ledColor.copy(alpha = 0.45f),
                                    spotColor = ledColor
                                )
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

                    WaveformSeekbar(
                        songId = currentSongId ?: "",
                        progress = progress.coerceIn(0f, 1f),
                        onSeek = { player.seekTo((it * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
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
                        val prevSource = rememberInteractionSource()
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .then(if (isTv) Modifier.tvFocusable(prevSource) else Modifier)
                                .clickable(interactionSource = prevSource, indication = null) { player.previous() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.SkipPrevious, "Bài trước", modifier = Modifier.size(36.dp), tint = Color.White)
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp).size(80.dp)
                        ) {
                            if (state.isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .graphicsLayer {
                                            scaleX = haloScale
                                            scaleY = haloScale
                                            alpha = haloAlpha
                                        }
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                )
                            }
                            val playPauseSource = rememberInteractionSource()
                            Box(
                                Modifier.size(64.dp)
                                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                    .then(if (isTv) Modifier.tvFocusable(playPauseSource) else Modifier)
                                    .clickable(interactionSource = playPauseSource, indication = null) { player.togglePlay() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Phát/Dừng", modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }

                        val nextSource = rememberInteractionSource()
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .then(if (isTv) Modifier.tvFocusable(nextSource) else Modifier)
                                .clickable(interactionSource = nextSource, indication = null) { player.next() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.SkipNext, "Bài sau", modifier = Modifier.size(36.dp), tint = Color.White)
                        }
                    }

                    AudiophileReviewPanel(
                        songId = currentSongId ?: "",
                        songDetails = songDetails,
                        onSongDetailsChanged = { songDetails = it },
                        reviewNote = reviewNote,
                        onReviewNoteChanged = { note ->
                            reviewNote = note
                            currentSongId?.let { id ->
                                sharedPrefs.edit().putString(id, note).apply()
                            }
                        }
                    )

                    Spacer(Modifier.height(24.dp))

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
                            val hardwareNote = activeDevice.hardwareNote
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
                            .aspectRatio(1f)
                            .swipeGestures(
                                onSwipeDown = onClose,
                                onSwipeLeft = { player.next() },
                                onSwipeRight = { player.previous() }
                            ),
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
                                .shadow(
                                    elevation = artShadow,
                                    shape = RoundedCornerShape(artCorner),
                                    clip = false,
                                    ambientColor = ledColor.copy(alpha = 0.45f),
                                    spotColor = ledColor
                                )
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
                        WaveformSeekbar(
                            songId = currentSongId ?: "",
                            progress = progress.coerceIn(0f, 1f),
                            onSeek = { player.seekTo((it * duration).toLong()) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp).size(80.dp)
                        ) {
                            if (state.isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .graphicsLayer {
                                            scaleX = haloScale
                                            scaleY = haloScale
                                            alpha = haloAlpha
                                        }
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                )
                            }
                            Box(
                                Modifier.size(64.dp)
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
                item {
                    AudiophileReviewPanel(
                        songId = currentSongId ?: "",
                        songDetails = songDetails,
                        onSongDetailsChanged = { songDetails = it },
                        reviewNote = reviewNote,
                        onReviewNoteChanged = { note ->
                            reviewNote = note
                            currentSongId?.let { id ->
                                sharedPrefs.edit().putString(id, note).apply()
                            }
                        }
                    )
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
                    val hardwareNote = activeDevice.hardwareNote
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

        if (isScreensaverActive && isTv) {
            AmbientScreensaverMode(
                artworkUri = art,
                title = meta?.title?.toString().orEmpty(),
                artist = meta?.artist?.toString().orEmpty(),
                onDismiss = { isScreensaverActive = false }
            )
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
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.25f))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .width(3.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.1f))
                            )
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f).padding(bottom = 16.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (subValue.isNotEmpty()) {
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC4BBA6),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiophileReviewPanel(
    songId: String,
    songDetails: me.troly.nhac.data.subsonic.Song?,
    onSongDetailsChanged: (me.troly.nhac.data.subsonic.Song) -> Unit,
    reviewNote: String,
    onReviewNoteChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    
    var isEditingReview by remember { mutableStateOf(false) }
    var reviewInput by remember(reviewNote) { mutableStateOf(reviewNote) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0CFFFFFF))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ĐÁNH GIÁ CHẤT LƯỢNG MASTERING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }
        
        // Rating Stars & Heart Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val currentRating = songDetails?.userRating ?: 0
                (1..5).forEach { starIndex ->
                    val active = starIndex <= currentRating
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Rate $starIndex stars",
                        tint = if (active) Color(0xFFFFB300) else Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable {
                                val newRating = if (currentRating == starIndex) 0 else starIndex
                                scope.launch {
                                    try {
                                        repo.setRating(songId, newRating)
                                        songDetails?.let { onSongDetailsChanged(it.copy(userRating = newRating)) }
                                        Toast.makeText(context, "Đã cập nhật đánh giá: $newRating sao", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Lỗi cập nhật đánh giá", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(4.dp)
                    )
                }
            }
            
            val isStarred = songDetails?.starred != null
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isStarred) Color(0x15FF1744) else Color(0x0AFFFFFF))
                    .border(
                        width = 0.5.dp,
                        color = if (isStarred) Color(0xFFFF1744).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
                    .clickable {
                        scope.launch {
                            try {
                                if (isStarred) {
                                    repo.unstar(songId)
                                    songDetails?.let { onSongDetailsChanged(it.copy(starred = null)) }
                                    Toast.makeText(context, "Đã bỏ yêu thích", Toast.LENGTH_SHORT).show()
                                } else {
                                    repo.star(songId)
                                    songDetails?.let { onSongDetailsChanged(it.copy(starred = "starred")) }
                                    Toast.makeText(context, "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi cập nhật yêu thích", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Yêu thích",
                    tint = if (isStarred) Color(0xFFFF1744) else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(14.dp))
        
        // Review Notes Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GHI CHÚ TRẢI NGHIỆM NGHE NHẠC",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E8E93)
            )
            
            if (!isEditingReview) {
                IconButton(
                    onClick = { isEditingReview = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Sửa ghi chú",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (isEditingReview) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = reviewInput,
                    onValueChange = { reviewInput = it },
                    placeholder = {
                        Text(
                            "Ví dụ: Bản thu SACD chất lượng cao, dải trầm ấm áp, sân khấu rộng mở, độ động cực kỳ chi tiết...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 5
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            reviewInput = reviewNote
                            isEditingReview = false
                        }
                    ) {
                        Text("Huỷ", color = Color.White.copy(alpha = 0.6f))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            onReviewNoteChanged(reviewInput)
                            isEditingReview = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lưu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEditingReview = true }
                    .background(Color(0x06FFFFFF), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                if (reviewNote.isNotBlank()) {
                    Text(
                        text = reviewNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC4BBA6),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        lineHeight = 20.sp
                    )
                } else {
                    Text(
                        text = "Chưa có đánh giá cho bản thu này. Nhấp để ghi lại cảm nhận âm trường, chi tiết, hay thiết bị phối ghép phù hợp...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.25f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AmbientScreensaverMode(
    artworkUri: Any?,
    title: String,
    artist: String,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "screensaver_float")
    val xOffsetState by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "xOffset"
    )
    val yOffsetState by infiniteTransition.animateFloat(
        initialValue = -0.2f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 31000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset"
    )

    // Breathing halo glow animation for artwork edge
    val glowTransition = rememberInfiniteTransition(label = "screensaver_glow")
    val glowRadius by glowTransition.animateFloat(
        initialValue = 10f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )

    // Clock state
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val dateFormat = java.text.SimpleDateFormat("EEEE, dd MMMM", java.util.Locale.getDefault())
        while (true) {
            val now = java.util.Calendar.getInstance().time
            timeText = timeFormat.format(now)
            dateText = dateFormat.format(now)
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Multi-layer deep blurred ambient background
        AnimatedAmbientBackground(artworkUri = artworkUri)
        
        // Dark overlay scrim to make it extremely subtle and OLED-friendly
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        // Top-Right Clock & Date Panel
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 50.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 64.sp,
                letterSpacing = (-1).sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = dateText.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC4BBA6).copy(alpha = 0.65f),
                letterSpacing = 1.5.sp
            )
        }

        // Floating Artwork and Song Metadata Container
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight

            Row(
                modifier = Modifier
                    .offset(
                        x = screenWidth * xOffsetState,
                        y = screenHeight * yOffsetState
                    )
                    .width(380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFFC4BBA6).copy(alpha = 0.25f),
                                Color.Transparent,
                                Color(0xFFC4BBA6).copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glow rounded cover art
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(
                            elevation = glowRadius.dp,
                            shape = RoundedCornerShape(16.dp),
                            clip = false,
                            ambientColor = Color(0xFFC4BBA6).copy(alpha = 0.15f),
                            spotColor = Color(0xFFC4BBA6).copy(alpha = 0.35f)
                        )
                ) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                Spacer(Modifier.width(20.dp))

                // Metadata details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC4BBA6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Bottom 16-band Grandiose Silk Spectrum visualizer
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp, start = 80.dp, end = 80.dp)
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            val activeColor = Color(0xFFC4BBA6).copy(alpha = 0.65f)
            
            val visualizerTransition = rememberInfiniteTransition(label = "screensaver_vis")
            val heights = remember {
                (0 until 16).map { i ->
                    val duration = 400 + (i * 59) % 450
                    val target = 0.4f + (i % 4) * 0.15f
                    duration to target
                }
            }.mapIndexed { i, (duration, target) ->
                visualizerTransition.animateFloat(
                    initialValue = 0.1f,
                    targetValue = target,
                    animationSpec = infiniteRepeatable(
                        animation = tween(duration, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ss_bar_h_$i"
                )
            }

            for (i in 0 until 16) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight(heights[i].value)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(activeColor, activeColor.copy(alpha = 0.1f))
                            ),
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

