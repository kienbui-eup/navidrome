package me.troly.nhac.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.troly.nhac.playback.AudioDeviceHelper
import me.troly.nhac.playback.AudioDecisionEngine
import me.troly.nhac.data.subsonic.isServerTranscodeSuffix
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.player.InteractiveDspSelectors
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.PathEffect

@Composable
fun SettingsScreen() {
    val repo = LocalRepo.current
    val cfg = repo.config
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pingResult by remember { mutableStateOf<String?>(null) }
    var isAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(repo) {
        isAdmin = repo.checkAdminStatus()
    }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    // Audiophile hardware monitoring & DSP collection
    val player = LocalPlayer.current
    val playerState by player.state.collectAsState()
    val meta = playerState.current

    var activeDevice by remember { mutableStateOf(AudioDeviceHelper.getActiveDeviceDetails(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDevice = AudioDeviceHelper.getActiveDeviceDetails(context)
            delay(2000)
        }
    }

    // Load active settings states
    val activeFilter by player.activeFilter.collectAsState()
    val activeDither by player.activeDither.collectAsState()
    val activeModulator by player.activeModulator.collectAsState()

    val bitPerfectEnabled by player.bitPerfectEnabled.collectAsState()
    val aaudioEnabled by player.aaudioEnabled.collectAsState()
    val bufferSizeSetting by player.bufferSize.collectAsState()

    val extras = meta?.extras
    val suffix = extras?.getString("suffix")
    val bitRate = extras?.getInt("bitRate") ?: 0
    val bitDepth = extras?.getInt("bitDepth") ?: 0
    val samplingRate = extras?.getInt("samplingRate") ?: 0

    // Compute dynamic report using AudioDecisionEngine (fallback to CD Lossless specs if paused)
    val audioReport = remember(activeDevice, suffix, bitDepth, samplingRate, bitRate, activeFilter, activeDither, activeModulator) {
        AudioDecisionEngine.determineAudioPath(
            activeDevice = activeDevice,
            sourceSuffix = suffix ?: "flac",
            sourceBitDepth = if (bitDepth > 0) bitDepth else 16,
            sourceSamplingRate = if (samplingRate > 0) samplingRate else 44100,
            sourceBitRate = if (bitRate > 0) bitRate else 1411,
            selectedFilter = activeFilter,
            selectedDither = activeDither,
            selectedModulator = activeModulator
        )
    }

    val ledColor = audioReport.ledColor
    val isDspEnabled = audioReport.isUpsampled

    val (formatName, originalSpecs) = remember(suffix, bitDepth, samplingRate, bitRate) {
        formatOriginalSpecs(suffix ?: "flac", if (bitDepth > 0) bitDepth else 16, if (samplingRate > 0) samplingRate else 44100, if (bitRate > 0) bitRate else 1411)
    }
    val isTranscoded = remember(suffix) { isServerTranscodeSuffix(suffix) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Cài đặt",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )

        // 1. ĐƯỜNG TRUYỀN TÍN HIỆU HIỆN TẠI (AUDIO SIGNAL PATH)
        SettingsCard(Icons.Filled.GraphicEq, "ĐƯỜNG TRUYỀN TÍN HIỆU HIỆN TẠI (AUDIO SIGNAL PATH)") {
            Text(
                text = if (meta == null) "Hệ thống hiển thị ở chế độ cấu hình trước (Demo Mode)" else "Đang hiển thị chuẩn đo lường thời gian thực (Real-time Link)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SignalPathFlowMap(
                ledColor = ledColor,
                bitPerfectEnabled = bitPerfectEnabled,
                aaudioEnabled = aaudioEnabled,
                isTranscoded = isTranscoded,
                formatName = formatName,
                originalSpecs = originalSpecs,
                outputName = activeDevice.name,
                isDspEnabled = isDspEnabled,
                activeFilter = activeFilter,
                activeDither = activeDither,
                activeModulator = activeModulator
            )

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0AFFFFFF))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                SignalPathStep(
                    title = "1. NGUỒN NHẠC GỐC (ORIGINAL FILE)",
                    value = formatName,
                    subValue = originalSpecs,
                    isFirst = true,
                    color = ledColor
                )

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
                    color = ledColor,
                    content = {
                        InteractiveDspSelectors(
                            activeFilter = activeFilter,
                            activeDither = activeDither,
                            activeModulator = activeModulator,
                            onFilterSelected = { player.setFilter(it) },
                            onDitherSelected = { player.setDither(it) },
                            onModulatorSelected = { player.setModulator(it) }
                        )
                    }
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

        // 2. TỐI ƯU HÓA ÂM THANH THIẾT BỊ (DEVICE AUDIO OPTIMIZATION)
        SettingsCard(Icons.Filled.GraphicEq, "TỐI ƯU HÓA ÂM THANH THIẾT BỊ") {
            // Exclusive Bit-Perfect Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Độc quyền USB Bit-Perfect (Exclusive Mode)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Bỏ qua bộ trộn Android (AudioFlinger), truyền tải bit-perfect trực tiếp tới USB DAC ngoài.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = bitPerfectEnabled,
                    onCheckedChange = { player.setBitPerfectEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 6.dp))

            // AAudio Engine Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Bộ giải mã AAudio Engine",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Kích hoạt AAudio luồng thấp cho tai nghe dây hoặc Bluetooth HD (LDAC, aptX Adaptive).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = aaudioEnabled,
                    onCheckedChange = { player.setAaudioEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 6.dp))

            // Buffer Sizing Option
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Kích thước Bộ nhớ đệm (Audio Buffer Sizing)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Mức bộ đệm lớn giúp TV Box yếu như X96 M300 hoạt động ổn định, loại bỏ hoàn toàn tiếng nổ lách tách.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sizeOptions = listOf("512 frames", "Balanced Standard", "2048 frames")
                    sizeOptions.forEach { option ->
                        val isSelected = bufferSizeSetting == option
                        val bgCol = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x0AFFFFFF)
                        val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFC4BBA6)
                        val label = when (option) {
                            "512 frames" -> "Siêu thấp (512)"
                            "Balanced Standard" -> "Tiêu chuẩn (1024)"
                            "2048 frames" -> "TV Box Ổn định (2048)"
                            else -> option
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgCol)
                                .clickable { player.setBufferSize(option) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                    }
                }
            }
        }

        // 3. MÁY CHỦ (SERVER)
        SettingsCard(Icons.Filled.Dns, "Máy chủ") {
            InfoLine("Địa chỉ", cfg.baseUrl)
            InfoLine("Tài khoản", cfg.username.ifBlank { "—" })
            pingResult?.let { InfoLine("Trạng thái", it) }
            OutlinedButton(
                onClick = {
                    pingResult = "Đang kiểm tra…"
                    scope.launch {
                        pingResult = runCatching { repo.ping() }
                            .fold({ if (it) "Kết nối tốt" else "Không phản hồi" }, { "Lỗi: ${it.message}" })
                    }
                },
                modifier = Modifier.padding(top = 10.dp),
            ) { Text("Kiểm tra kết nối") }
        }

        // 4. ADMIN STATUS
        if (isAdmin) {
            var scanStatus by remember { mutableStateOf<String?>(null) }
            SettingsCard(Icons.Filled.Dns, "Quản trị / Admin") {
                Text(
                    "Quản lý máy chủ nhạc và tài nguyên hệ thống.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                scanStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                OutlinedButton(
                    onClick = {
                        scanStatus = "Đang gửi yêu cầu quét thư viện…"
                        scope.launch {
                            try {
                                val success = repo.triggerLibraryScan()
                                if (success) {
                                    scanStatus = "Yêu cầu thành công: Đang quét thư viện!"
                                    Toast.makeText(context, "Đã bắt đầu quét thư viện thành công!", Toast.LENGTH_LONG).show()
                                } else {
                                    scanStatus = "Lỗi: Không thể khởi động quét"
                                    Toast.makeText(context, "Lỗi: Không thể khởi động quét", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                scanStatus = "Lỗi: ${e.message}"
                                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 10.dp),
                ) { Text("Quét lại thư viện (Rescan)") }
            }
        }

        // 5. GIỚI THIỆU (ABOUT)
        SettingsCard(Icons.Filled.Info, "Giới thiệu") {
            InfoLine("Ứng dụng", "vi2play")
            InfoLine("Phiên bản", version)
        }
    }
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Column(Modifier.padding(top = 10.dp)) { content() }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SignalPathStep(
    title: String,
    value: String,
    subValue: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    color: Color,
    content: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Line and Node column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 4.dp)
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
                        .weight(1f)
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

        Column(modifier = Modifier
            .weight(1f)
            .padding(bottom = 16.dp)) {
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
            if (content != null) {
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}

private fun formatOriginalSpecs(suffix: String, bitDepth: Int, samplingRate: Int, bitRate: Int): Pair<String, String> {
    val fmt = suffix.lowercase()
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

@Composable
private fun SignalPathFlowMap(
    ledColor: Color,
    bitPerfectEnabled: Boolean,
    aaudioEnabled: Boolean,
    isTranscoded: Boolean,
    formatName: String,
    originalSpecs: String,
    outputName: String,
    isDspEnabled: Boolean,
    activeFilter: String,
    activeDither: String,
    activeModulator: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flow_map_anim")
    
    val ledScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led_glow_scale"
    )
    
    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowing_line"
    )

    val scrollState = rememberScrollState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PipelineNode(
            stageLabel = "SOURCE",
            title = if (formatName.contains("DSD")) "DSD" else "FLAC / WAV",
            desc = originalSpecs.substringBefore(" •"),
            ledColor = Color(0xFFE0E0E0),
            isActive = true,
            ledScale = ledScale
        )

        FlowConnector(flowPhase = flowPhase, color = ledColor, isActive = true)

        val streamTitle = if (isTranscoded) "Transcoded" else "Direct"
        val streamDesc = if (isTranscoded) "24-bit FLAC" else "Original Bitstream"
        PipelineNode(
            stageLabel = "STREAM",
            title = streamTitle,
            desc = streamDesc,
            ledColor = if (isTranscoded) Color(0xFF42A5F5) else Color(0xFF66BB6A),
            isActive = true,
            ledScale = ledScale
        )

        FlowConnector(flowPhase = flowPhase, color = ledColor, isActive = true)

        val dspTitle = if (isDspEnabled) "HQ DSP" else "Bit-Perfect Engine"
        val dspDesc = if (isDspEnabled) activeFilter.substringBefore("-").trim() else "Bypassed / None"
        PipelineNode(
            stageLabel = "ENGINE",
            title = dspTitle,
            desc = dspDesc,
            ledColor = if (isDspEnabled) Color(0xFFAB47BC) else Color(0xFF66BB6A),
            isActive = true,
            ledScale = ledScale
        )

        FlowConnector(flowPhase = flowPhase, color = ledColor, isActive = true)

        val driverTitle = if (bitPerfectEnabled && outputName.contains("USB")) "USB Exclusive" else if (aaudioEnabled) "AAudio Driver" else "Android Mixer"
        val driverDesc = if (bitPerfectEnabled && outputName.contains("USB")) "Bit-Perfect Output" else if (aaudioEnabled) "Low-Latency" else "Resampled to 48kHz"
        val driverColor = if (bitPerfectEnabled && outputName.contains("USB")) Color(0xFFFFB300) else if (aaudioEnabled) Color(0xFF26A69A) else Color(0xFFFFA726)
        PipelineNode(
            stageLabel = "DRIVER",
            title = driverTitle,
            desc = driverDesc,
            ledColor = driverColor,
            isActive = true,
            ledScale = ledScale,
            isBypassedStyle = !bitPerfectEnabled && outputName.contains("USB")
        )

        FlowConnector(flowPhase = flowPhase, color = ledColor, isActive = true)

        PipelineNode(
            stageLabel = "OUTPUT",
            title = outputName.take(14) + if (outputName.length > 14) ".." else "",
            desc = if (outputName.contains("USB")) "External DAC" else "Built-in / BT",
            ledColor = ledColor,
            isActive = true,
            ledScale = ledScale
        )
    }
}

@Composable
private fun PipelineNode(
    stageLabel: String,
    title: String,
    desc: String,
    ledColor: Color,
    isActive: Boolean,
    ledScale: Float,
    isBypassedStyle: Boolean = false
) {
    val alphaMultiplier = if (isBypassedStyle) 0.35f else 1.0f
    
    Box(
        modifier = Modifier
            .size(width = 110.dp, height = 82.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x12FFFFFF).copy(alpha = 0.08f * alphaMultiplier))
            .border(
                0.5.dp, 
                if (isBypassedStyle) Color.White.copy(alpha = 0.02f) else ledColor.copy(alpha = 0.22f), 
                RoundedCornerShape(10.dp)
            )
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stageLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    color = (if (isBypassedStyle) Color.Gray else ledColor).copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )
                
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(6.dp * if (isActive && !isBypassedStyle) ledScale else 1f)
                            .clip(CircleShape)
                            .background(ledColor.copy(alpha = 0.35f * alphaMultiplier))
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(ledColor.copy(alpha = alphaMultiplier))
                    )
                }
            }
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = alphaMultiplier),
                    maxLines = 1,
                    fontSize = 11.sp
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFC4BBA6).copy(alpha = 0.8f * alphaMultiplier),
                    maxLines = 1,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun FlowConnector(
    flowPhase: Float,
    color: Color,
    isActive: Boolean
) {
    Canvas(
        modifier = Modifier
            .width(24.dp)
            .height(16.dp)
    ) {
        val h = size.height
        val w = size.width
        
        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = androidx.compose.ui.geometry.Offset(0f, h / 2),
            end = androidx.compose.ui.geometry.Offset(w, h / 2),
            strokeWidth = 2.dp.toPx()
        )
        
        if (isActive) {
            drawLine(
                color = color.copy(alpha = 0.85f),
                start = androidx.compose.ui.geometry.Offset(0f, h / 2),
                end = androidx.compose.ui.geometry.Offset(w, h / 2),
                strokeWidth = 2.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(12f, 12f),
                    phase = -flowPhase
                )
            )
        }
    }
}
