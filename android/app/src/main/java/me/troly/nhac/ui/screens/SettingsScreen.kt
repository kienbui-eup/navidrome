package me.troly.nhac.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import me.troly.nhac.ui.LocalRepo

// Audiophile settings additions
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.playback.AudioDeviceHelper

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

    // Audiophile hardware monitoring additions
    val player = LocalPlayer.current
    val playerState by player.state.collectAsState()
    val meta = playerState.current
    
    var activeDevice by remember { mutableStateOf(AudioDeviceHelper.getActiveDeviceDetails(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDevice = AudioDeviceHelper.getActiveDeviceDetails(context)
            kotlinx.coroutines.delay(2000)
        }
    }
    
    val extras = meta?.extras
    val suffix = extras?.getString("suffix")
    val bitRate = extras?.getInt("bitRate") ?: 0
    val bitDepth = extras?.getInt("bitDepth") ?: 0
    val samplingRate = extras?.getInt("samplingRate") ?: 0
    
    val currentSourceText = remember(meta, suffix, bitDepth, samplingRate, bitRate) {
        if (meta == null) {
            "Không phát nhạc (Sẵn sàng)"
        } else {
            val fmt = suffix?.uppercase() ?: "AUDIO"
            val resolution = if (bitDepth > 0 && samplingRate > 0) {
                "${bitDepth}-bit / ${samplingRate / 1000.0} kHz"
            } else if (bitRate > 0) {
                "${bitRate} kbps"
            } else {
                "Chuẩn"
            }
            "$fmt • $resolution"
        }
    }
    
    val isDsdOrHighRes = remember(suffix, bitDepth) {
        suffix?.lowercase() in setOf("dsf", "dff", "dsd") || bitDepth >= 24
    }
    
    val (ledColor, forecastTitle, forecastDesc) = remember(meta, activeDevice, isDsdOrHighRes) {
        if (meta == null) {
            Triple(
                Color(0xFF8E8E93), // Gray
                "Chờ tín hiệu nhạc",
                "Phát nhạc để đo lường đường truyền tín hiệu."
            )
        } else {
            when {
                activeDevice.isLossless && isDsdOrHighRes -> Triple(
                    Color(0xFF00E676), // Green
                    "Bit-Perfect Lossless (Trực tiếp)",
                    "Tín hiệu tinh khiết tuyệt đối! Luồng dữ liệu giải mã Bit-Perfect trực tiếp qua phần cứng ngoài."
                )
                activeDevice.isHiResCapable -> Triple(
                    Color(0xFF29B6F6), // Blue
                    "Hi-Res Wireless (Không dây cao cấp)",
                    "Đang phát Bluetooth chất lượng cao (LDAC/SSC). Vui lòng bật HD Audio trong cài đặt Bluetooth."
                )
                else -> Triple(
                    Color(0xFFFFB300), // Amber
                    "Standard Quality (Chất lượng tiêu chuẩn)",
                    "Bị giới hạn bởi loa tích hợp hoặc băng thông kết nối. Khuyên dùng USB DAC hoặc tai nghe dây."
                )
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Cài đặt",
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )

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

        SettingsCard(Icons.Filled.GraphicEq, "Âm thanh & Thiết bị") {
            InfoLine("Thiết bị ra", activeDevice.name)
            InfoLine("Loại kết nối", activeDevice.techLabel)
            InfoLine("Hỗ trợ tối đa", activeDevice.maxQualityForecast)
            InfoLine("Nguồn nhạc", currentSourceText)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ledColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = forecastTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ledColor
                        )
                        Text(
                            text = forecastDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            
            Text(
                "Mẹo: Hệ thống luôn tự động tối ưu dải động bằng luồng âm thanh PCM 32-bit Float không nén trước khi định tuyến qua phần cứng đầu ra.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

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

        SettingsCard(Icons.Filled.Info, "Giới thiệu") {
            InfoLine("Ứng dụng", "vi2play")
            InfoLine("Phiên bản", version)
        }
    }
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
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
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground)
    }
}
