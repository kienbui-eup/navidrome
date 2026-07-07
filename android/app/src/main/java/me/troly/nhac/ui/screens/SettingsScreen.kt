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
import kotlinx.coroutines.launch
import me.troly.nhac.ui.LocalRepo

@Composable
fun SettingsScreen() {
    val repo = LocalRepo.current
    val cfg = repo.config
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pingResult by remember { mutableStateOf<String?>(null) }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
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

        SettingsCard(Icons.Filled.GraphicEq, "Âm thanh") {
            InfoLine("Chất lượng", "Nguyên bản (bit-perfect)")
            InfoLine("Đầu ra", "Ưu tiên USB DAC khi cắm")
            Text(
                "Phát nguyên gốc không nén lại, xuất trực tiếp qua DAC USB để giữ đúng bit và tần số lấy mẫu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
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
