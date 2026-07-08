package me.troly.nhac.ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import me.troly.nhac.playback.AudioDeviceHelper
import me.troly.nhac.playback.ActiveDeviceDetails
import me.troly.nhac.data.subsonic.isServerTranscodeSuffix

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import me.troly.nhac.ui.LocalPlayer
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

    // Interactive Audio Quality Badges & Visualizer State
    val context = androidx.compose.ui.platform.LocalContext.current
    var activeDevice by remember { mutableStateOf(AudioDeviceHelper.getActiveDeviceDetails(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDevice = AudioDeviceHelper.getActiveDeviceDetails(context)
            kotlinx.coroutines.delay(1500)
        }
    }
    
    val extras = meta?.extras
    val suffix = extras?.getString("suffix")
    val bitRate = extras?.getInt("bitRate") ?: 0
    val bitDepth = extras?.getInt("bitDepth") ?: 0
    val samplingRate = extras?.getInt("samplingRate") ?: 0
    
    val isDsdOrHighRes = remember(suffix, bitDepth) {
        suffix?.lowercase() in setOf("dsf", "dff", "dsd") || bitDepth >= 24
    }
    
    val ledColor = remember(activeDevice, isDsdOrHighRes) {
        when {
            activeDevice.isLossless && isDsdOrHighRes -> Color(0xFF00E676) // Pristine Green (Hi-Res Bit-Perfect)
            activeDevice.isHiResCapable -> Color(0xFF29B6F6) // HD Wireless Blue
            else -> Color(0xFFFFB300) // Standard Amber
        }
    }
    
    var showSignalPath by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Blurred artwork backdrop (blur applies on Android 12+, degrades gracefully)
        AsyncImage(
            model = art, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(48.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xCC0B0B0D), Color(0xF20B0B0D))),
            ),
        )

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Đóng",
                    tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = art, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(0.84f).aspectRatio(1f)
                        .shadow(24.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Text(
                meta?.title?.toString() ?: "",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = Color.White, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).basicMarquee(),
            )
            Text(
                meta?.artist?.toString() ?: "",
                style = MaterialTheme.typography.titleMedium, color = Color(0xFFC4BBA6),
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )

            // Interactive Audio Quality Badges & Visualizer Row
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSignalPath = true }
                    .background(Color(0x11FFFFFF))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Audio Visualizer (Left)
                AudioVisualizer(isPlaying = state.isPlaying, modifier = Modifier.padding(end = 12.dp))
                
                // 2. Suffix badge (e.g. FLAC, MP3)
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
                
                // 3. Quality text (e.g. 24-bit / 96.0 kHz)
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
                
                // 4. Output label summary
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
                
                // 5. LED Status Dot (Right)
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ledColor)
                )
            }

            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { scrubbing = true; scrubValue = it },
                onValueChangeFinished = { player.seekTo((scrubValue * duration).toLong()); scrubbing = false },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color(0x33FFFFFF),
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(state.positionMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
                Text(formatMs(state.durationMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4BBA6))
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Bài trước", modifier = Modifier.size(40.dp), tint = Color.White)
                }
                Box(
                    Modifier.padding(horizontal = 20.dp).size(76.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .clickable { player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Phát/Dừng", modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Bài sau", modifier = Modifier.size(40.dp), tint = Color.White)
                }
            }
        }

        // Audiophile Signal Path Panel Overlay (Slide-up Glassmorphism Style)
        if (showSignalPath) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showSignalPath = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFF131317))
                        .clickable(enabled = true, onClick = {}) // block click pass-through
                        .systemBarsPadding()
                        .padding(24.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ledColor)
                            )
                            Text(
                                text = "ĐƯỜNG TRUYỀN TÍN HIỆU (SIGNAL PATH)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        IconButton(onClick = { showSignalPath = false }) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Đóng",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        val (formatName, originalSpecs) = remember(suffix, bitDepth, samplingRate, bitRate) {
                            formatOriginalSpecs(suffix, bitDepth, samplingRate, bitRate)
                        }
                        
                        // Step 1: Source
                        SignalPathStep(
                            title = "1. NGUỒN NHẠC GỐC (ORIGINAL FILE)",
                            value = formatName,
                            subValue = originalSpecs,
                            isFirst = true,
                            color = ledColor
                        )
                        
                        // Step 2: Streaming
                        val isTranscoded = remember(suffix) { isServerTranscodeSuffix(suffix) }
                        val streamingValue = if (isTranscoded) "Server Transcoded (FLAC 24-bit)" else "Direct Stream (Nguyên bản)"
                        val streamingSub = if (isTranscoded) {
                            "Dữ liệu gốc định dạng $suffix được máy chủ tự động chuyển mã không hao tổn sang FLAC 24-bit PCM với tần số lấy mẫu nguyên bản, giúp ứng dụng Android giải mã tối ưu."
                        } else {
                            "Tệp âm thanh ${suffix?.uppercase() ?: "âm thanh"} được truyền phát trực tiếp ở chất lượng nguyên gốc từ máy chủ, không qua bất kỳ khâu xử lý hay tái nén nào."
                        }
                        SignalPathStep(
                            title = "2. TRUYỀN PHÁT THỰC TẾ (STREAMING STREAM)",
                            value = streamingValue,
                            subValue = streamingSub,
                            color = ledColor
                        )
                        
                        // Step 3: Engine
                        val engineValue = "ExoPlayer Audio Engine (Float 32-bit)"
                        val engineSub = "Ứng dụng tự động nâng cấp kỹ thuật số không hao tổn (Lossless Upscaling) lên Float PCM 32-bit, giúp giữ dải động (dynamic range) tối đa và giảm thiểu méo tiếng."
                        SignalPathStep(
                            title = "3. BỘ GIẢI MÃ ỨNG DỤNG (PLAYBACK ENGINE)",
                            value = engineValue,
                            subValue = engineSub,
                            color = ledColor
                        )
                        
                        // Step 4: Connection & Output Device
                        val deviceDetails = "${activeDevice.techLabel} • ${activeDevice.maxQualityForecast}\n${activeDevice.description}"
                        SignalPathStep(
                            title = "4. THIẾT BỊ ĐẦU RA (OUTPUT HARDWARE)",
                            value = activeDevice.name,
                            subValue = deviceDetails,
                            color = ledColor
                        )
                        
                        // Step 5: Verdict & Forecast
                        val verdictTitle = when {
                            ledColor == Color(0xFF00E676) -> "Bit-Perfect Lossless (Trải nghiệm đỉnh cao)"
                            ledColor == Color(0xFF29B6F6) -> "Hi-Res Wireless (Không dây chất lượng cao)"
                            else -> "Standard Quality (Chất lượng tiêu chuẩn)"
                        }
                        val verdictSub = when {
                            ledColor == Color(0xFF00E676) -> "Tín hiệu đạt độ tinh khiết tối đa! Luồng dữ liệu hoàn toàn không nén, bỏ qua bộ trộn hệ điều hành và truyền trực tiếp ở chế độ Bit-Perfect qua phần cứng kết nối."
                            ledColor == Color(0xFF29B6F6) -> "Đường truyền không dây cao cấp. Đảm bảo codec chất lượng cao (LDAC hoặc Seamless Codec) đang hoạt động trong Cài đặt Bluetooth trên điện thoại của bạn để trải nghiệm trọn vẹn dải động 24-bit."
                            else -> "Thiết bị phát (như loa tích hợp) hoặc tệp nhạc bị giới hạn băng tần. Khuyên dùng tai nghe chuyên dụng hoặc USB DAC ngoài."
                        }
                        SignalPathStep(
                            title = "5. DỰ BÁO CHẤT LƯỢNG ĐẠT ĐƯỢC (AUDIO FORECAST)",
                            value = verdictTitle,
                            subValue = verdictSub,
                            isLast = true,
                            color = ledColor
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
