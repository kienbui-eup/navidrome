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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border


@Composable
fun InteractiveDspSelectors(
    activeFilter: String,
    activeDither: String,
    activeModulator: String,
    onFilterSelected: (String) -> Unit,
    onDitherSelected: (String) -> Unit,
    onModulatorSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x0AFFFFFF))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Resampler Filter Option
        Column {
            Text(
                "Filter (Bộ lọc nội suy):",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val filterOptions = listOf("Bypass", "poly-sinc-xtr-lp", "Closed-Form-M", "sinc-L")
                filterOptions.forEach { option ->
                    val isSelected = activeFilter == option
                    val bgCol = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x11FFFFFF)
                    val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFC4BBA6)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(bgCol)
                            .clickable { onFilterSelected(option) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.split("-").last(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textCol,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // 2. Dither Option
        Column {
            Text(
                "Dither (Bộ phân dither):",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val ditherOptions = listOf("None", "Gauss dither", "NS9 (Shaper)", "LNS15")
                ditherOptions.forEach { option ->
                    val isSelected = activeDither == option
                    val bgCol = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x11FFFFFF)
                    val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFC4BBA6)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(bgCol)
                            .clickable { onDitherSelected(option) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.split(" ")[0],
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textCol,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // 3. Modulator Option
        Column {
            Text(
                "Modulator (Bộ điều chế SDM):",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modulatorOptions = listOf("PCM (Bit-Perfect)", "DSD64 (Sigma-Delta)", "DSD512 (HQ-grade)")
                modulatorOptions.forEach { option ->
                    val isSelected = activeModulator == option
                    val bgCol = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x11FFFFFF)
                    val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFC4BBA6)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(bgCol)
                            .clickable { onModulatorSelected(option) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.split(" ")[0],
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textCol,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HiResSignalCapsule(
    isPlaying: Boolean,
    suffix: String?,
    bitRate: Int,
    bitDepth: Int,
    samplingRate: Int,
    activeDevice: me.troly.nhac.playback.ActiveDeviceDetails,
    ledColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Badges group (Format, Specs)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val player = LocalPlayer.current
            val bitPerfectEnabled by player.bitPerfectEnabled.collectAsState()
            val isUsbDac = activeDevice.typeLabel == "USB DAC" || activeDevice.name.contains("USB", ignoreCase = true)
            
            if (bitPerfectEnabled && isUsbDac) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x2E00E676)) // Emerald green glow background
                        .border(0.7.dp, Color(0xFF00E676).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "BIT-PERFECT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.sp,
                            color = Color(0xFF00E676),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (!suffix.isNullOrBlank()) {
                val isDsd = suffix.lowercase() in setOf("dsf", "dff", "dsd")
                val isFlac = suffix.lowercase() == "flac"
                val badgeBgColor = when {
                    isDsd -> Color(0xFFFFB300).copy(alpha = 0.2f)
                    isFlac -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else -> Color(0x22FFFFFF)
                }
                val badgeTextColor = when {
                    isDsd -> Color(0xFFFFB300)
                    isFlac -> MaterialTheme.colorScheme.primary
                    else -> Color.White
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBgColor)
                        .border(0.5.dp, badgeTextColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = suffix.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x11FFFFFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = null,
                            tint = Color(0xFFC4BBA6),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = qualityText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE5DECD)
                        )
                    }
                }
            }
        }

        // Row 2: Target Path & LED
        val outputLabel = remember(activeDevice) {
            when {
                activeDevice.typeLabel == "USB DAC" -> "Direct USB DAC"
                activeDevice.name.contains("Buds2 Pro", ignoreCase = true) -> "Buds 2 Pro (SSC)"
                activeDevice.name.contains("UP5", ignoreCase = true) -> "UP5 (LDAC)"
                else -> activeDevice.typeLabel
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x0EFFFFFF))
                .border(0.5.dp, ledColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ledColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "➔ $outputLabel",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ledColor
            )
        }
    }
}


@Composable
private fun RoonSignalPathDialog(
    songId: String,
    songTitle: String,
    artistName: String,
    suffix: String?,
    bitRate: Int,
    bitDepth: Int,
    samplingRate: Int,
    activeDevice: me.troly.nhac.playback.ActiveDeviceDetails,
    activeFilter: String,
    activeDither: String,
    activeModulator: String,
    bitPerfectEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val repo = LocalRepo.current
    var audiophileData by remember(songId) { mutableStateOf<me.troly.nhac.data.subsonic.AudiophileResponse?>(null) }
    var isLoading by remember(songId) { mutableStateOf(true) }
    var loadError by remember(songId) { mutableStateOf<String?>(null) }

    LaunchedEffect(songId) {
        isLoading = true
        loadError = null
        try {
            val response = repo.getSongAudiophile(songId)
            audiophileData = response
        } catch (e: Exception) {
            loadError = e.localizedMessage ?: "Không thể kết nối đến máy chủ"
        } finally {
            isLoading = false
        }
    }

    val isRealLossless = audiophileData?.realLossless ?: (suffix?.lowercase() in setOf("flac", "dsf", "dff", "dsd", "wav", "m4a", "alac"))
    val cutoff = audiophileData?.cutoffFrequency ?: 22.0
    val drScore = audiophileData?.dynamicRangeScore ?: 10

    val isUsbDac = activeDevice.typeLabel == "USB DAC" || activeDevice.name.contains("USB", ignoreCase = true)
    val isDspActive = activeFilter != "Bypass" || activeDither != "None" || activeModulator != "PCM (Bit-Perfect)"

    val signalQuality = when {
        !isRealLossless -> "Low-Quality / Degraded (Phát hiện Fake Lossless)"
        drScore < 6 -> "Low-Quality (Nén dải động cực lớn)"
        bitPerfectEnabled && isUsbDac -> "Lossless (Bit-Perfect)"
        isDspActive -> "Enhanced (Xử lý DSP chất lượng cao)"
        else -> "Lossless (Tiêu chuẩn)"
    }

    val glowColor = when {
        !isRealLossless || drScore < 6 -> Color(0xFFE57373) // Coral Red
        signalQuality.contains("Enhanced") -> Color(0xFF81C784) // Emerald Green
        signalQuality.contains("Bit-Perfect") -> Color(0xFFBA68C8) // Royal Purple (Roon Purple)
        else -> Color(0xFF64B5F6) // Cyan Blue
    }

    val pulseTransition = rememberInfiniteTransition(label = "signal_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFA121212)),
            border = BorderStroke(1.dp, glowColor.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SƠ ĐỒ ĐƯỜNG ĐI TÍN HIỆU",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFC4BBA6),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = signalQuality.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = glowColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Đóng",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Interactive vertical signal path representation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Node 1: Source File
                    SignalNode(
                        title = "Tệp gốc (Source)",
                        subtitle = "${suffix?.uppercase() ?: "AUDIO"} • ${if (bitDepth > 0) "${bitDepth}-bit" else "${bitRate} kbps"} / ${samplingRate / 1000.0} kHz",
                        indicatorColor = glowColor,
                        isPulse = true,
                        pulseScale = pulseScale,
                        icon = Icons.Filled.Audiotrack
                    ) {
                        // Details underneath Source
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x08FFFFFF))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Tiêu đề: $songTitle",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Nghệ sĩ: $artistName",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            if (isLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.dp,
                                        color = glowColor
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Đang quét dải tần siêu cao (GCP VM)...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFC4BBA6),
                                        fontSize = 9.sp
                                    )
                                }
                            } else if (loadError != null) {
                                Text(
                                    text = "Quét máy chủ: ${loadError}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE57373),
                                    fontSize = 9.sp
                                )
                            } else if (audiophileData != null) {
                                val data = audiophileData!!
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Tần số cắt: ${data.cutoffFrequency} kHz",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (data.realLossless) Color(0xFF81C784) else Color(0xFFE57373),
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = "Nhận diện: " + if (data.realLossless) "✓ Lossless thật" else "⚠️ Fake Lossless / Mp3 upscale",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (data.realLossless) Color(0xFF81C784) else Color(0xFFE57373),
                                            fontSize = 9.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (data.dynamicRangeScore >= 10) Color(0x2EBA68C8) else Color(0x2EE57373))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ĐỘ ĐỘNG DR${data.dynamicRangeScore}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (data.dynamicRangeScore >= 10) Color(0xFFBA68C8) else Color(0xFFE57373),
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Connection Line 1
                    SignalWire(color = glowColor)

                    // Node 2: Decoder Engine
                    val isTranscoding = me.troly.nhac.data.subsonic.isServerTranscodeSuffix(suffix)
                    SignalNode(
                        title = "Bộ giải mã (Decoder)",
                        subtitle = if (isTranscoding) "Transcode trên GCP VM ➔ 24-bit FLAC PCM" else "Media3 Native Codec Engine",
                        indicatorColor = if (isTranscoding) Color(0xFFFFB300) else glowColor,
                        isPulse = false,
                        pulseScale = 1f,
                        icon = Icons.Filled.Check
                    )

                    // Connection Line 2
                    SignalWire(color = glowColor)

                    // Node 3: DSP Processing (Resampler, Dither, Modulator)
                    SignalNode(
                        title = "Mạch xử lý số (DSP Engine)",
                        subtitle = if (isDspActive) "Đang hoạt động (Enhanced)" else "Bypass (Đường truyền thuần khiết)",
                        indicatorColor = if (isDspActive) Color(0xFF81C784) else Color.White.copy(alpha = 0.3f),
                        isPulse = isDspActive,
                        pulseScale = pulseScale,
                        icon = Icons.Filled.Edit
                    ) {
                        if (isDspActive) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x08FFFFFF))
                                    .padding(8.dp)
                            ) {
                                if (activeFilter != "Bypass") {
                                    Text(
                                        text = "• Resampler: $activeFilter",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF81C784),
                                        fontSize = 10.sp
                                    )
                                }
                                if (activeDither != "None") {
                                    Text(
                                        text = "• Dither: $activeDither",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF81C784),
                                        fontSize = 10.sp
                                    )
                                }
                                if (activeModulator != "PCM (Bit-Perfect)") {
                                    Text(
                                        text = "• Modulator: $activeModulator",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF81C784),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    // Connection Line 3
                    SignalWire(color = glowColor)

                    // Node 4: Output Driver / Device
                    val outputTitle = if (bitPerfectEnabled && isUsbDac) "Bit-Perfect Output (Direct USB)" else "Android Audio Mixer"
                    SignalNode(
                        title = outputTitle,
                        subtitle = "${activeDevice.name} (${activeDevice.typeLabel})",
                        indicatorColor = glowColor,
                        isPulse = true,
                        pulseScale = pulseScale,
                        icon = Icons.Filled.VolumeUp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer description
                Text(
                    text = when {
                        !isRealLossless -> "Chú ý: File nhạc hiện tại đã bị phát hiện là nhạc giả Lossless (Fake Lossless). Dải tần số siêu cao bị cắt bỏ hoàn toàn tại ngưỡng ${cutoff} kHz do nén lossy trước đó."
                        bitPerfectEnabled && isUsbDac -> "Đường truyền Bit-Perfect hoàn hảo, tín hiệu âm thanh được bỏ qua hoàn toàn mixer của Android và truyền trực tiếp đến DAC phần cứng của bạn không hao hụt."
                        isDspActive -> "Tín hiệu âm thanh được tối ưu hoá thông qua bộ lọc nội suy chất lượng cao và bộ phân dither để tăng cường độ chi tiết dải động."
                        else -> "Tín hiệu âm thanh truyền dẫn dạng lossless tiêu chuẩn đến thiết bị phát."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC4BBA6),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SignalNode(
    title: String,
    subtitle: String,
    indicatorColor: Color,
    isPulse: Boolean,
    pulseScale: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Dot indicator
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPulse) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .clip(CircleShape)
                        .background(indicatorColor.copy(alpha = 0.25f))
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = indicatorColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC4BBA6),
                fontSize = 11.sp
            )
            content?.invoke()
        }
    }
}

@Composable
private fun SignalWire(color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 7.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.4f), color)
                    )
                )
        )
    }
}


private fun getFrequencyXRatio(freq: Float, bounds: FloatArray): Float {
    if (bounds.size < 33) return 0.5f
    for (i in 0 until 32) {
        val low = bounds[i]
        val high = bounds[i + 1]
        if (freq >= low && freq <= high) {
            val bandFraction = (freq - low) / (high - low)
            return (i + bandFraction) / 32f
        }
    }
    if (freq > bounds[32]) return 1f
    return 0f
}

/**
 * A majestic, pro-grade Real-Time 16/32-Band FFT Spectrum Analyzer.
 * Displays real-time audio frequencies with glowing bar gradients and gentle spring physics.
 * Can be tapped to expand into a High-Resolution 32-Band Spectrogram featuring high-frequency
 * markers and real-time lossy compression vs. true lossless/Hi-Res integrity diagnostics.
 */
@Composable
fun RealTimeSpectrumAnalyzer(
    isPlaying: Boolean,
    ledColor: Color,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp < 360 || configuration.screenHeightDp < 650
    var isExpanded by remember(isSmallScreen) { mutableStateOf(!isSmallScreen) }
    var visualizerMode by remember { mutableStateOf(0) } // 0 = Spectrum + Curves, 1 = Oscilloscope, 2 = VU Meter
    
    val realAmplitudes by me.troly.nhac.playback.AudioVisualizerHelper.amplitudes.collectAsState()
    val realAmplitudes32 by me.troly.nhac.playback.AudioVisualizerHelper.amplitudes32.collectAsState()
    val rawWaveform by me.troly.nhac.playback.AudioVisualizerHelper.rawWaveform.collectAsState()
    val rmsLevel by me.troly.nhac.playback.AudioVisualizerHelper.rmsLevel.collectAsState()
    
    val losslessVerdict by me.troly.nhac.playback.AudioVisualizerHelper.losslessVerdict.collectAsState()
    val losslessConfidence by me.troly.nhac.playback.AudioVisualizerHelper.losslessConfidence.collectAsState()
    val cutoffFrequency by me.troly.nhac.playback.AudioVisualizerHelper.cutoffFrequency.collectAsState()
    
    val trackSR by me.troly.nhac.playback.AudioVisualizerHelper.trackSamplingRate.collectAsState()
    val trackBD by me.troly.nhac.playback.AudioVisualizerHelper.trackBitDepth.collectAsState()
    val trackSuffix by me.troly.nhac.playback.AudioVisualizerHelper.trackSuffix.collectAsState()
    val captureSR by me.troly.nhac.playback.AudioVisualizerHelper.captureSamplingRate.collectAsState()
    val dynamicBounds by me.troly.nhac.playback.AudioVisualizerHelper.dynamicBoundaries.collectAsState()

    // Fallback animated procedural heights for when paused/no native active data (16 bands)
    val transition = rememberInfiniteTransition(label = "rt_procedural")
    val proceduralHeights = remember {
        (0 until 16).map { i ->
            val duration = 280 + (i * 47) % 350
            val target = 0.35f + (i % 4) * 0.15f
            duration to target
        }
    }.mapIndexed { i, (duration, target) ->
        transition.animateFloat(
            initialValue = 0.1f,
            targetValue = target,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rt_p_h_$i"
        )
    }

    // Fallback animated procedural heights for 32 bands
    val proceduralHeights32 = remember {
        (0 until 32).map { i ->
            val duration = 250 + (i * 31) % 280
            val target = 0.3f + (i % 5) * 0.12f
            duration to target
        }
    }.mapIndexed { i, (duration, target) ->
        transition.animateFloat(
            initialValue = 0.1f,
            targetValue = target,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rt_p_h32_$i"
        )
    }

    // Check if there is any active real amplitude data pulsing (non-flat)
    val isNativeActive = realAmplitudes.any { it > 0.12f }
    val activeColor = ledColor

    // Animate container height and layout when expanded (slight height boost to fit multiple modes and selectors)
    val animatedHeightDp by animateDpAsState(
        targetValue = if (isExpanded) 260.dp else 72.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 250f),
        label = "rt_container_h"
    )

    val baseModifier = modifier
        .height(animatedHeightDp)

    val clickableModifier = if (!isExpanded) {
        baseModifier.clickable { isExpanded = true }
    } else {
        baseModifier
    }

    val containerPadding = if (isExpanded) {
        PaddingValues(0.dp)
    } else {
        PaddingValues(vertical = 8.dp, horizontal = 0.dp)
    }

    Column(
        modifier = clickableModifier.padding(containerPadding),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (!isExpanded) {
            // --- COLLAPSED VIEW: Classic 16-Band ---
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (i in 0 until 16) {
                    val rawHeight = if (isNativeActive) {
                        realAmplitudes.getOrElse(i) { 0.1f }
                    } else {
                        proceduralHeights[i].value
                    }

                    val animatedHeight by animateFloatAsState(
                        targetValue = if (isPlaying) rawHeight else 0.05f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                        label = "rt_bar_h_$i"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(animatedHeight)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        activeColor.copy(alpha = 0.95f),
                                        activeColor.copy(alpha = 0.4f),
                                        activeColor.copy(alpha = 0.08f)
                                    )
                                ),
                                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                            )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Collapsed text hint & quick info
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "30Hz",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, color = Color(0xFFC4BBA6).copy(alpha = 0.5f))
                )
                Text(
                    "Bấm để soi Phổ âm Lossless & Hi-Res (32-Band FFT)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, color = ledColor.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                )
                Text(
                    if (trackSR > 48000) "${trackSR / 1000f}k" else "20k+",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, color = Color(0xFFC4BBA6).copy(alpha = 0.5f))
                )
            }
        } else {
            // --- EXPANDED VIEW: Multi-Mode Audiophile Suite (Full Screen Edge-to-Edge) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { 
                        // Cycling through visualizer modes when tapped directly
                        visualizerMode = (visualizerMode + 1) % 3 
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Floating top indicator row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modeLabel = when (visualizerMode) {
                            0 -> "BỘ SOI PHỔ TẦN & EQ CURVES"
                            1 -> "DAO ĐỘNG KÝ (OSCILLOSCOPE)"
                            else -> "ĐỒNG HỒ VU CƠ HỌC (ANALOG)"
                        }
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ledColor,
                                letterSpacing = 1.sp
                            )
                        )

                        // Elegant collapse badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { isExpanded = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Thu nhỏ ↘",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }

                    // Main content box based on visualizerMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                    when (visualizerMode) {
                        0 -> {
                            // --- MODE 0: 32-Band FFT Spectrum with Overlaid 8 Parametric Curves ---
                            // 1. Grid reference lines (horizontal) & Cut-off guidelines
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val lines = listOf(0.33f, 0.66f)
                                lines.forEach { ratio ->
                                    val y = size.height * ratio
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.05f),
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = 1f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                    )
                                }

                                val bounds = dynamicBounds
                                val isNativeHiRes = trackSR > 48000 || trackSuffix.lowercase() in listOf("dsf", "dff", "dsd")

                                if (isNativeHiRes) {
                                    val limitF = if (trackSR > 96000) 80000f else 40000f
                                    val x20k = size.width * getFrequencyXRatio(20000f, bounds)
                                    val xHiRes = size.width * getFrequencyXRatio(limitF, bounds)

                                    drawLine(
                                        color = Color(0xFF00E676).copy(alpha = 0.25f), // Green CD limit (20kHz)
                                        start = androidx.compose.ui.geometry.Offset(x20k, 0f),
                                        end = androidx.compose.ui.geometry.Offset(x20k, size.height),
                                        strokeWidth = 1.5f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                    )
                                    drawLine(
                                        color = Color(0xFFFFC107).copy(alpha = 0.35f), // Gold Hi-Res Peak (40kHz or 80kHz)
                                        start = androidx.compose.ui.geometry.Offset(xHiRes, 0f),
                                        end = androidx.compose.ui.geometry.Offset(xHiRes, size.height),
                                        strokeWidth = 1.5f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                    )
                                } else {
                                    val x16k = size.width * getFrequencyXRatio(16000f, bounds)
                                    val x20k = size.width * getFrequencyXRatio(20000f, bounds)

                                    drawLine(
                                        color = Color(0xFFFF3D00).copy(alpha = 0.25f), // Red 16k MP3 limit
                                        start = androidx.compose.ui.geometry.Offset(x16k, 0f),
                                        end = androidx.compose.ui.geometry.Offset(x16k, size.height),
                                        strokeWidth = 1.5f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                    )
                                    drawLine(
                                        color = Color(0xFF00E676).copy(alpha = 0.25f), // Green 20k CD limit
                                        start = androidx.compose.ui.geometry.Offset(x20k, 0f),
                                        end = androidx.compose.ui.geometry.Offset(x20k, size.height),
                                        strokeWidth = 1.5f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                    )
                                }
                            }

                            // 2. Draw 32 Bars
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val bounds = dynamicBounds
                                val isNativeHiRes = trackSR > 48000 || trackSuffix.lowercase() in listOf("dsf", "dff", "dsd")

                                for (i in 0 until 32) {
                                    val rawHeight = if (isNativeActive) {
                                        realAmplitudes32.getOrElse(i) { 0.1f }
                                    } else {
                                        proceduralHeights32[i].value
                                    }

                                    val animatedHeight by animateFloatAsState(
                                        targetValue = if (isPlaying) rawHeight else 0.05f,
                                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 280f),
                                        label = "rt_bar32_h_$i"
                                    )

                                    val barColor = if (isNativeHiRes) {
                                        val currentF = bounds.getOrElse(i) { 0f }
                                        when {
                                            currentF >= 20000f -> Color(0xFFFFD54F) // Golden glow for ultra high-res (>20kHz)
                                            currentF >= 10000f -> Color(0xFF00E676) // Green for mid-high presence
                                            else -> activeColor
                                        }
                                    } else {
                                        when {
                                            i >= 27 -> Color(0xFF00E676) // >20kHz (True Lossless Peak)
                                            i >= 23 -> Color(0xFF00B0FF) // 16kHz - 20kHz (High presence)
                                            else -> activeColor
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(animatedHeight)
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        barColor.copy(alpha = 0.95f),
                                                        barColor.copy(alpha = 0.4f),
                                                        barColor.copy(alpha = 0.05f)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(topStart = 1.5.dp, topEnd = 1.5.dp)
                                            )
                                    )
                                }
                            }

                            // 3. Drawing Overlaid 8 Parametric Colored Curves (Bell-shaped EQ Filters)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val curves = listOf(
                                    Pair(2, Color(0xFFE53935)),   // Bass - Red
                                    Pair(6, Color(0xFFFB8C00)),   // Low-Mid - Orange
                                    Pair(10, Color(0xFFFFD54F)),  // Mid 1 - Yellow/Gold
                                    Pair(14, Color(0xFF4CAF50)),  // Mid 2 - Green
                                    Pair(18, Color(0xFF00B0FF)),  // Hi-Mid 1 - Cyan
                                    Pair(22, Color(0xFF1E88E5)),  // Hi-Mid 2 - Blue
                                    Pair(26, Color(0xFF8E24AA)),  // Treble - Purple
                                    Pair(30, Color(0xFFF06292))   // Ultra Treble - Pink
                                )

                                curves.forEach { (bandIdx, color) ->
                                    val rawHeight = if (isNativeActive) {
                                        realAmplitudes32.getOrElse(bandIdx) { 0.1f }
                                    } else {
                                        proceduralHeights32[bandIdx].value
                                    }
                                    
                                    val animAmp = if (isPlaying) rawHeight else 0.05f
                                    val path = Path()
                                    
                                    val peakX = size.width * ((bandIdx + 0.5f) / 32f)
                                    val peakY = size.height - (animAmp * size.height * 0.85f).coerceAtLeast(4.dp.toPx())
                                    val sigma = size.width * 0.11f // Bell width

                                    path.moveTo(0f, size.height)
                                    for (x in 0..size.width.toInt() step 4) {
                                        val dx = x - peakX
                                        val factor = Math.exp((- (dx * dx) / (2.0 * sigma * sigma)).toDouble()).toFloat()
                                        val y = size.height - (animAmp * size.height * 0.85f) * factor
                                        path.lineTo(x.toFloat(), y)
                                    }
                                    path.lineTo(size.width, size.height)
                                    path.close()

                                    // Filled glassmorphic filter curves
                                    drawPath(
                                        path = path,
                                        color = color.copy(alpha = 0.06f)
                                    )
                                    // Smooth colored outline stroke
                                    drawPath(
                                        path = path,
                                        color = color.copy(alpha = 0.65f),
                                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                        }

                        1 -> {
                            // --- MODE 1: High-Fi Oscilloscope (Waveform) ---
                            val timeOffset by transition.animateFloat(
                                initialValue = 0f,
                                targetValue = 2 * Math.PI.toFloat(),
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2500, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "oscilloscope_time"
                            )
                            
                            Box(modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = Color(0xFFFF4081).copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .size(14.dp)
                                )
                                
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height
                                    val centerY = height / 2f
                                    
                                    // Draw horizontal grid lines
                                    val gridLinesCount = 6
                                    for (j in 1 until gridLinesCount) {
                                        val y = height * (j.toFloat() / gridLinesCount)
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.07f),
                                            start = androidx.compose.ui.geometry.Offset(0f, y),
                                            end = androidx.compose.ui.geometry.Offset(width, y),
                                            strokeWidth = 1f
                                        )
                                    }
                                    
                                    // Draw vertical grid lines
                                    val vertLinesCount = 10
                                    for (j in 1 until vertLinesCount) {
                                        val x = width * (j.toFloat() / vertLinesCount)
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.04f),
                                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                                            end = androidx.compose.ui.geometry.Offset(x, height),
                                            strokeWidth = 1f
                                        )
                                    }
                                    
                                    // Draw dashed center line
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.16f),
                                        start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                        end = androidx.compose.ui.geometry.Offset(width, centerY),
                                        strokeWidth = 1.5f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                    )
                                    
                                    // Compute path
                                    val points = 128
                                    val path = Path()
                                    
                                    for (idx in 0 until points) {
                                        val x = width * (idx.toFloat() / (points - 1))
                                        val waveVal = if (isNativeActive && isPlaying) {
                                            rawWaveform.getOrElse(idx) { 0f }
                                        } else if (isPlaying) {
                                            // Fallback procedural waveform (composite harmonics)
                                            val normX = (idx.toFloat() / points) * 2f * Math.PI.toFloat()
                                            (0.35f * Math.sin((normX * 4f + timeOffset).toDouble()) +
                                             0.15f * Math.sin((normX * 9f - timeOffset * 1.5f).toDouble()) +
                                             0.08f * Math.sin((normX * 18f + timeOffset * 2f).toDouble())).toFloat()
                                        } else {
                                            // Subtle background white noise
                                            (Math.random() * 0.02 - 0.01).toFloat()
                                        }
                                        
                                        val y = centerY + waveVal * centerY * 0.85f
                                        if (idx == 0) {
                                            path.moveTo(x, y)
                                        } else {
                                            path.lineTo(x, y)
                                        }
                                    }
                                    
                                    // Outer neon glow
                                    drawPath(
                                        path = path,
                                        color = Color(0xFFFF4081).copy(alpha = 0.15f),
                                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                    // Main solid wave line
                                    drawPath(
                                        path = path,
                                        color = Color(0xFFFF4081).copy(alpha = 0.85f),
                                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                        }

                        2 -> {
                            // --- MODE 2: Retro Analog VU Meter ---
                            val needleSpringSpec = spring<Float>(
                                dampingRatio = 0.58f, // Classic spring overshoot
                                stiffness = 160f      // Mechanical ballistic momentum
                            )
                            
                            val proceduralRms = if (isPlaying) {
                                transition.animateFloat(
                                    initialValue = 0.15f,
                                    targetValue = 0.85f,
                                    animationSpec = infiniteRepeatable(
                                        animation = keyframes {
                                            durationMillis = 1800
                                            0.2f at 0 with FastOutSlowInEasing
                                            0.75f at 300 with LinearOutSlowInEasing
                                            0.4f at 600 with FastOutLinearInEasing
                                            0.85f at 1000 with LinearOutSlowInEasing
                                            0.15f at 1400 with FastOutSlowInEasing
                                        },
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "vu_fallback_val"
                                ).value
                            } else {
                                0f
                            }
                            
                            val targetLevel = if (isNativeActive && isPlaying) {
                                rmsLevel
                            } else if (isPlaying) {
                                proceduralRms
                            } else {
                                0f
                            }
                            
                            val animatedLevel by animateFloatAsState(
                                targetValue = targetLevel.coerceIn(0f, 1f),
                                animationSpec = needleSpringSpec,
                                label = "vu_needle_level"
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF111111), RoundedCornerShape(10.dp))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            ) {
                                // Retro warm backlight glow
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0x35FFA726), // Orange incandescent glow
                                                    Color.Transparent
                                                ),
                                                radius = 300f
                                            )
                                        )
                                )
                                
                                Canvas(modifier = Modifier.fillMaxSize().padding(top = 10.dp)) {
                                    val width = size.width
                                    val height = size.height
                                    val pivotX = width / 2f
                                    val pivotY = height * 0.98f
                                    val maxRadius = height * 0.82f
                                    
                                    val startAngle = 230f
                                    val endAngle = 310f
                                    val sweepAngle = endAngle - startAngle
                                    
                                    // Scale background path
                                    drawArc(
                                        color = Color.White.copy(alpha = 0.15f),
                                        startAngle = startAngle,
                                        sweepAngle = sweepAngle,
                                        useCenter = false,
                                        style = Stroke(width = 1.dp.toPx()),
                                        topLeft = androidx.compose.ui.geometry.Offset(pivotX - maxRadius, pivotY - maxRadius),
                                        size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
                                    )
                                    
                                    // Red warn zone (>0dB)
                                    val redZoneStart = startAngle + sweepAngle * 0.78f
                                    val redZoneSweep = sweepAngle * 0.22f
                                    drawArc(
                                        color = Color(0xFFE53935).copy(alpha = 0.75f),
                                        startAngle = redZoneStart,
                                        sweepAngle = redZoneSweep,
                                        useCenter = false,
                                        style = Stroke(width = 3.dp.toPx()),
                                        topLeft = androidx.compose.ui.geometry.Offset(pivotX - maxRadius, pivotY - maxRadius),
                                        size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
                                    )
                                    
                                    // Render ticks
                                    val ticksCount = 20
                                    for (j in 0..ticksCount) {
                                        val fraction = j.toFloat() / ticksCount
                                        val tickAngle = startAngle + fraction * sweepAngle
                                        val rad = Math.toRadians(tickAngle.toDouble())
                                        
                                        val isMajor = j % 2 == 0
                                        val tickLen = if (isMajor) 9.dp.toPx() else 4.5.dp.toPx()
                                        val isRed = tickAngle >= redZoneStart
                                        val tickColor = if (isRed) Color(0xFFE53935) else Color(0xFFD4CDBC).copy(alpha = 0.65f)
                                        
                                        val startX = pivotX + (maxRadius - tickLen) * Math.cos(rad).toFloat()
                                        val startY = pivotY + (maxRadius - tickLen) * Math.sin(rad).toFloat()
                                        val endX = pivotX + maxRadius * Math.cos(rad).toFloat()
                                        val endY = pivotY + maxRadius * Math.sin(rad).toFloat()
                                        
                                        drawLine(
                                            color = tickColor,
                                            start = androidx.compose.ui.geometry.Offset(startX, startY),
                                            end = androidx.compose.ui.geometry.Offset(endX, endY),
                                            strokeWidth = (if (isMajor) 1.5.dp else 0.8.dp).toPx()
                                        )
                                        
                                        // Tick labels
                                        if (isMajor && j % 4 == 0) {
                                            val labelVal = when (j) {
                                                0 -> "-20"
                                                4 -> "-10"
                                                8 -> "-5"
                                                12 -> "-1"
                                                16 -> "0"
                                                20 -> "+3"
                                                else -> ""
                                            }
                                            val labelRadius = maxRadius - 18.dp.toPx()
                                            val labelX = pivotX + labelRadius * Math.cos(rad).toFloat()
                                            val labelY = pivotY + labelRadius * Math.sin(rad).toFloat()
                                            
                                            drawContext.canvas.nativeCanvas.drawText(
                                                labelVal,
                                                labelX,
                                                labelY,
                                                android.graphics.Paint().apply {
                                                    color = tickColor.toArgb()
                                                    textSize = 7.5.sp.toPx()
                                                    typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
                                                    textAlign = android.graphics.Paint.Align.CENTER
                                                }
                                            )
                                        }
                                    }
                                    
                                    // Text inside VU face
                                    drawContext.canvas.nativeCanvas.drawText(
                                        "VU LEVEL / CH-SYNC",
                                        pivotX,
                                        pivotY - maxRadius * 0.45f,
                                        android.graphics.Paint().apply {
                                            color = Color.White.copy(alpha = 0.25f).toArgb()
                                            textSize = 8.sp.toPx()
                                            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
                                            textAlign = android.graphics.Paint.Align.CENTER
                                        }
                                    )
                                    drawContext.canvas.nativeCanvas.drawText(
                                        "ANALOG BALLISTICS",
                                        pivotX,
                                        pivotY - maxRadius * 0.3f,
                                        android.graphics.Paint().apply {
                                            color = Color(0xFFFF5722).copy(alpha = 0.4f).toArgb()
                                            textSize = 6.5.sp.toPx()
                                            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
                                            textAlign = android.graphics.Paint.Align.CENTER
                                        }
                                    )
                                    
                                    // Draw needle with shadow
                                    val needleAngle = startAngle + animatedLevel * sweepAngle
                                    val needleRad = Math.toRadians(needleAngle.toDouble())
                                    val needleEndX = pivotX + (maxRadius - 3.dp.toPx()) * Math.cos(needleRad).toFloat()
                                    val needleEndY = pivotY + (maxRadius - 3.dp.toPx()) * Math.sin(needleRad).toFloat()
                                    
                                    // Shadow
                                    drawLine(
                                        color = Color.Black.copy(alpha = 0.35f),
                                        start = androidx.compose.ui.geometry.Offset(pivotX + 2.dp.toPx(), pivotY + 1.dp.toPx()),
                                        end = androidx.compose.ui.geometry.Offset(needleEndX + 2.dp.toPx(), needleEndY + 1.dp.toPx()),
                                        strokeWidth = 1.8.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    
                                    // Mechanical needle (High-visibility Red-Orange)
                                    drawLine(
                                        color = Color(0xFFFF5722),
                                        start = androidx.compose.ui.geometry.Offset(pivotX, pivotY),
                                        end = androidx.compose.ui.geometry.Offset(needleEndX, needleEndY),
                                        strokeWidth = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    
                                    // Pivot Cap
                                    drawCircle(
                                        color = Color(0xFF1E1E12),
                                        radius = 11.dp.toPx(),
                                        center = androidx.compose.ui.geometry.Offset(pivotX, pivotY)
                                    )
                                    drawCircle(
                                        color = Color(0xFF333333),
                                        radius = 11.dp.toPx(),
                                        center = androidx.compose.ui.geometry.Offset(pivotX, pivotY),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                    drawCircle(
                                        color = Color(0xFFFF5722),
                                        radius = 3.5.dp.toPx(),
                                        center = androidx.compose.ui.geometry.Offset(pivotX, pivotY)
                                    )
                                }
                                
                                // Close button "X" inside VU Face
                                IconButton(
                                    onClick = { visualizerMode = 0 }, // Switches back to spectrum analyzer
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Thoát",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                
                                // Metallic Sensitivity Potentiometer
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(18.dp)
                                        .background(
                                            brush = Brush.sweepGradient(
                                                colors = listOf(
                                                    Color(0xFF666666),
                                                    Color(0xFFCCCCCC),
                                                    Color(0xFF333333),
                                                    Color(0xFFCCCCCC),
                                                    Color(0xFF666666)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .width(1.dp)
                                            .height(5.dp)
                                            .background(Color.Black.copy(alpha = 0.8f))
                                    )
                                }
                            }
                        }
                    }
                }

                // Show frequency labels only in spectrum mode (Mode 0) to avoid crowding
                if (visualizerMode == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 7.5.sp, color = Color(0xFFC4BBA6).copy(alpha = 0.4f))
                        val isNativeHiRes = trackSR > 48000 || trackSuffix.lowercase() in listOf("dsf", "dff", "dsd")

                        if (isNativeHiRes) {
                            val limitLabel = if (trackSR > 96000) "80kHz" else "40kHz"
                            Text("20Hz", style = labelStyle)
                            Text("2kHz", style = labelStyle)
                            Text("10kHz", style = labelStyle)
                            Text("20kHz (CD)", style = labelStyle.copy(color = Color(0xFF00E676).copy(alpha = 0.6f), fontWeight = FontWeight.Bold))
                            Text("$limitLabel (Hi-Res)", style = labelStyle.copy(color = Color(0xFFFFC107).copy(alpha = 0.7f), fontWeight = FontWeight.Bold))
                            Text("${trackSR / 2000f}kHz", style = labelStyle)
                        } else {
                            Text("20Hz", style = labelStyle)
                            Text("1kHz", style = labelStyle)
                            Text("8kHz", style = labelStyle)
                            Text("16kHz (MP3-Cut)", style = labelStyle.copy(color = Color.Red.copy(alpha = 0.6f), fontWeight = FontWeight.Bold))
                            Text("20kHz (CD)", style = labelStyle.copy(color = Color.Green.copy(alpha = 0.6f), fontWeight = FontWeight.Bold))
                            Text("24kHz", style = labelStyle)
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)

                // Lossless Inspection Info Row (Displayed consistently for pro-grade diagnostics across all modes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Quality badge
                    val badgeColor = when (losslessVerdict) {
                        "VERIFIED HI-RES AUDIO" -> Color(0xFFFFA000) // Beautiful Gold
                        "ANDROID RESAMPLED (LIMIT 48k)" -> Color(0xFF00B0FF) // Cyan / Blue
                        "UPSCALED / FAKE HI-RES" -> Color(0xFFFF3D00) // Red
                        "VERIFIED PURE LOSSLESS" -> Color(0xFF00C853) // Green
                        "PROBABLE LOSSLESS" -> Color(0xFFFFAB00) // Amber
                        "COMPRESSED / LOSS_CUT" -> Color(0xFFFF3D00) // Red
                        else -> Color(0xFF00B0FF)
                    }
                    val badgeText = when (losslessVerdict) {
                        "VERIFIED HI-RES AUDIO" -> "✦ TRUE HI-RES GOLD"
                        "ANDROID RESAMPLED (LIMIT 48k)" -> "✦ RESAMPLED HI-RES"
                        "UPSCALED / FAKE HI-RES" -> "⚠ FAKE HI-RES (CD UPSCALED)"
                        "VERIFIED PURE LOSSLESS" -> "✓ REAL CD LOSSLESS"
                        "PROBABLE LOSSLESS" -> "⚠ PROBABLE LOSSLESS"
                        "COMPRESSED / LOSS_CUT" -> "⚠ FAKE / COMPRESSED"
                        else -> "⚡ ANALYZING..."
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(0.5.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        )
                    }

                    // 2. Statistics details
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column {
                            Text(
                                "EST. CUTOFF",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.5.sp, color = Color.White.copy(alpha = 0.35f))
                            )
                            Text(
                                if (cutoffFrequency > 0) "${(cutoffFrequency / 1000f)} kHz" else "Analyzing...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }

                        Column {
                            Text(
                                "MIXER CAP",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.5.sp, color = Color.White.copy(alpha = 0.35f))
                            )
                            Text(
                                "${captureSR / 1000f} kHz",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }

                        Column {
                            Text(
                                "INTEGRITY",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.5.sp, color = Color.White.copy(alpha = 0.35f))
                            )
                            Text(
                                if (losslessConfidence > 0) "${losslessConfidence.toInt()}%" else "Analyzing...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "REAL-TIME DIAGNOSIS",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.5.sp, color = Color.White.copy(alpha = 0.35f))
                            )
                            val desc = when (losslessVerdict) {
                                "VERIFIED HI-RES AUDIO" -> "Sóng siêu âm hoạt động mạnh >20kHz. Hi-Res chuẩn phòng thu master."
                                "ANDROID RESAMPLED (LIMIT 48k)" -> "Mixer Android giới hạn ở 48kHz. Hãy cắm USB DAC để nghe Hi-Res gốc!"
                                "UPSCALED / FAKE HI-RES" -> "Phát hiện giả Hi-Res. File được nâng khống từ nguồn CD hoặc lossy."
                                "VERIFIED PURE LOSSLESS" -> "Phổ âm mạnh >18kHz. Lossless xịn chuẩn CD."
                                "PROBABLE LOSSLESS" -> "Dải tần tốt. Có dấu hiệu lossless chuẩn."
                                "COMPRESSED / LOSS_CUT" -> "Bị giới hạn tần số. File fake nâng từ MP3."
                                else -> "Đang đo mẫu thử tần số cao..."
                            }
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp, color = Color(0xFFC4BBA6), fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
}


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

        // Submerged crisp high-resolution artwork layer
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = 0.16f
                    }
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
fun NowPlayingScreen(onClose: () -> Unit, onNavigateToSettings: () -> Unit = {}) {
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
    var isQueueExpanded by remember { mutableStateOf(false) }
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
    
    val bitPerfectEnabled by player.bitPerfectEnabled.collectAsState()
    var showSignalPathDialog by remember { mutableStateOf(false) }

    
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
    val isWideScreen = isTv || (configuration.screenWidthDp >= 600 && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)

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
                // COL 1: Left panel (Controls, Signal Path, Hardware notes, Review card)
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .swipeGestures(
                            onSwipeDown = onClose,
                            onSwipeLeft = { player.next() },
                            onSwipeRight = { player.previous() }
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Đóng",
                                tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        ) {
                            Text(
                                text = meta?.title?.toString() ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                text = meta?.artist?.toString() ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC4BBA6),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isTv) {
                            val ssSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            IconButton(
                                onClick = { isScreensaverActive = true },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .tvFocusable(ssSource)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Radio,
                                    contentDescription = "Màn hình chờ",
                                    tint = Color(0xFFC4BBA6),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Hi-Res Signal Capsule Badge (Widescreen)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showSignalPathDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        HiResSignalCapsule(
                            isPlaying = state.isPlaying,
                            suffix = suffix,
                            bitRate = bitRate,
                            bitDepth = bitDepth,
                            samplingRate = samplingRate,
                            activeDevice = activeDevice,
                            ledColor = ledColor
                        )
                    }

                    // Real-Time 16-Band FFT Spectrum Analyzer
                    RealTimeSpectrumAnalyzer(
                        isPlaying = state.isPlaying,
                        ledColor = ledColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp)
                    )

                    WaveformSeekbar(
                        songId = currentSongId ?: "",
                        progress = progress.coerceIn(0f, 1f),
                        onSeek = { player.seekTo((it * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
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

                    Spacer(Modifier.height(16.dp))

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
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x0DFFFFFF))
                                .border(0.5.dp, ledColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ledColor)
                            )
                            Text(
                                text = "${suffix?.uppercase() ?: "FLAC"} ${if (bitDepth > 0) "${bitDepth}-bit / " else ""}${if (samplingRate > 0) "${samplingRate / 1000.0} kHz" else "44.1 kHz"} • ${audioReport.statusLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Bộ lọc DSP: $activeFilter • Thiết bị: ${activeDevice.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC4BBA6),
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable { onNavigateToSettings() }
                                .padding(vertical = 10.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Xem chi tiết đường truyền & Cấu hình thiết bị ➜",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

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

                    Spacer(Modifier.height(24.dp))

                    // Reordered Review Panel placed at the very bottom
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

                // COL 2: Right panel (Queue, Radio Recommendations - DSP card removed as it is integrated)
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
                }
            }
        } else {
            // ── NARROW PORTRAIT STRUCTURAL LAYOUT (MOBILE COVER SCREEN) ──────────
            val animatedArtHeight by animateDpAsState(
                targetValue = if (isQueueExpanded) 0.dp else 180.dp,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                label = "art_height"
            )
            val animatedCapsuleHeight by animateDpAsState(
                targetValue = if (isQueueExpanded) 0.dp else 36.dp,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                label = "capsule_height"
            )
            val animatedMetaHeight by animateDpAsState(
                targetValue = if (isQueueExpanded) 0.dp else 56.dp,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                label = "meta_height"
            )
            val animatedSeekbarHeight by animateDpAsState(
                targetValue = if (isQueueExpanded) 0.dp else 54.dp,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                label = "seekbar_height"
            )
            val animatedSpacing by animateDpAsState(
                targetValue = if (isQueueExpanded) 0.dp else 12.dp,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                label = "vertical_spacing"
            )
            val artAlpha by animateFloatAsState(
                targetValue = if (isQueueExpanded) 0f else 1f,
                animationSpec = tween(durationMillis = 200),
                label = "art_alpha"
            )
            val capsuleAlpha by animateFloatAsState(
                targetValue = if (isQueueExpanded) 0f else 1f,
                animationSpec = tween(durationMillis = 200),
                label = "capsule_alpha"
            )
            val metaAlpha by animateFloatAsState(
                targetValue = if (isQueueExpanded) 0f else 1f,
                animationSpec = tween(durationMillis = 200),
                label = "meta_alpha"
            )
            val seekbarAlpha by animateFloatAsState(
                targetValue = if (isQueueExpanded) 0f else 1f,
                animationSpec = tween(durationMillis = 200),
                label = "seekbar_alpha"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(vertical = 8.dp)
                    .swipeGestures(
                        onSwipeUp = { isQueueExpanded = true },
                        onSwipeDown = {
                            if (isQueueExpanded) {
                                isQueueExpanded = false
                            } else {
                                onClose()
                            }
                        }
                    )
            ) {
                // PART 1: STICKY CONTROL CONSOLE (STATIONARY AT THE TOP)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                        .background(Color(0x1F141218))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        )
                ) {
                    // Darkening scrim gradient
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x11000000),
                                        Color(0x77000000)
                                    )
                                )
                            )
                    )

                    // Control content overlay
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(animatedSpacing)
                    ) {
                        // Header row (Back / close button) with compact title and artist details
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Đóng",
                                    tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            ) {
                                Text(
                                    text = meta?.title?.toString() ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = meta?.artist?.toString() ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC4BBA6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Redesigned Hi-Res Signal Capsule Badge (Responsive, wrap-resistant, technical density)
                        if (animatedCapsuleHeight > 5.dp) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedCapsuleHeight)
                                    .graphicsLayer { alpha = capsuleAlpha }
                                    .padding(horizontal = 20.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showSignalPathDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                HiResSignalCapsule(
                                    isPlaying = state.isPlaying,
                                    suffix = suffix,
                                    bitRate = bitRate,
                                    bitDepth = bitDepth,
                                    samplingRate = samplingRate,
                                    activeDevice = activeDevice,
                                    ledColor = ledColor
                                )
                            }
                        }

                        // Real-Time 32-Band FFT Spectrum Analyzer (Always Pinned) - NO HORIZONTAL PADDING = TRÂN WIDTH!
                        RealTimeSpectrumAnalyzer(
                            isPlaying = state.isPlaying,
                            ledColor = ledColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp)
                        )

                        // Progress WaveformSeekbar & times
                        if (animatedSeekbarHeight > 10.dp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedSeekbarHeight)
                                    .graphicsLayer { alpha = seekbarAlpha }
                                    .padding(horizontal = 20.dp)
                            ) {
                                WaveformSeekbar(
                                    songId = currentSongId ?: "",
                                    progress = progress.coerceIn(0f, 1f),
                                    onSeek = { player.seekTo((it * duration).toLong()) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                )
                                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatMs(state.positionMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                                    Text(formatMs(state.durationMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                                }
                            }
                        }

                        // Playback Buttons (Always Pinned)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp).padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { player.previous() }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.SkipPrevious, "Bài trước", modifier = Modifier.size(32.dp), tint = Color.White)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp).size(68.dp)
                            ) {
                                if (state.isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .graphicsLayer {
                                                scaleX = haloScale
                                                scaleY = haloScale
                                                alpha = haloAlpha
                                            }
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                    )
                                }
                                Box(
                                    Modifier.size(56.dp)
                                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                        .clickable { player.togglePlay() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Phát/Dừng", modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                            IconButton(onClick = { player.next() }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.SkipNext, "Bài sau", modifier = Modifier.size(32.dp), tint = Color.White)
                            }
                        }
                    }
                }

                // Interactive click-to-toggle Swipe Drag Handle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isQueueExpanded = !isQueueExpanded }
                        .padding(vertical = 10.dp)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(5.dp)
                            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.5.dp))
                    )
                }

                // PART 2: SCROLLABLE SHEET FOR ALL OTHER SUPPEMENTARY VIEWS
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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

                    // Autoplay Radio Card in scroll feed - extremely simplified & elegant glassmorphism
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x11FFFFFF))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Radio,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Tự động gợi ý",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Switch(
                                checked = autoRadioEnabled,
                                onCheckedChange = { recManager.setAutoRadioEnabled(it) }
                            )
                        }
                    }

                    // Show recommendations directly underneath if auto-radio is enabled
                    if (autoRadioEnabled) {
                        if (isRecLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else if (recSongs.isNotEmpty()) {
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
                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0x0DFFFFFF))
                                    .border(0.5.dp, ledColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ledColor)
                                )
                                Text(
                                    text = "${suffix?.uppercase() ?: "FLAC"} ${if (bitDepth > 0) "${bitDepth}-bit / " else ""}${if (samplingRate > 0) "${samplingRate / 1000.0} kHz" else "44.1 kHz"} • ${audioReport.statusLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "Bộ lọc DSP: $activeFilter • Thiết bị: ${activeDevice.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC4BBA6),
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .clickable { onNavigateToSettings() }
                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Xem chi tiết đường truyền & Cấu hình thiết bị ➜",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Dynamic Hardware Specs matching note in scroll feed
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

                    // Audiophile review panel placed at the absolute bottom of portrait scroll feed
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

        if (showSignalPathDialog && currentSongId != null) {
            RoonSignalPathDialog(
                songId = currentSongId,
                songTitle = meta?.title?.toString() ?: "",
                artistName = meta?.artist?.toString() ?: "",
                suffix = suffix ?: songDetails?.suffix,
                bitRate = if (bitRate > 0) bitRate else (songDetails?.bitRate ?: 0),
                bitDepth = if (bitDepth > 0) bitDepth else (songDetails?.bitDepth ?: 0),
                samplingRate = if (samplingRate > 0) samplingRate else (songDetails?.samplingRate ?: 0),
                activeDevice = activeDevice,
                activeFilter = activeFilter,
                activeDither = activeDither,
                activeModulator = activeModulator,
                bitPerfectEnabled = bitPerfectEnabled,
                onDismiss = { showSignalPathDialog = false }
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
            val realAmplitudes by me.troly.nhac.playback.AudioVisualizerHelper.amplitudes.collectAsState()
            val isNativeActive = realAmplitudes.any { it > 0.12f }
            
            val visualizerTransition = rememberInfiniteTransition(label = "screensaver_vis")
            val proceduralHeights = remember {
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
                val rawHeight = if (isNativeActive) {
                    realAmplitudes.getOrElse(i) { 0.1f }
                } else {
                    proceduralHeights[i].value
                }

                val animatedHeight by animateFloatAsState(
                    targetValue = rawHeight,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = 250f
                    ),
                    label = "ss_spring_h_$i"
                )

                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight(animatedHeight)
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

