package me.troly.nhac.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.ServerConfig
import me.troly.nhac.data.subsonic.SubsonicRepository

/**
 * Server login. On success it saves the config and calls [onConnected].
 * Giao diện đăng nhập cao cấp hỗ trợ hiệu ứng Ambient Background Glow và Glassmorphic Card.
 */
@Composable
fun LoginScreen(onConnected: (ServerConfig) -> Unit) {
    var url by remember { mutableStateOf("https://ms.troly.me") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top-left glowing aura
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        // Bottom-right glowing aura
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "vi2play",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Kết nối máy chủ Navidrome",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, Color(0x12FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("Địa chỉ máy chủ") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("Tên đăng nhập") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Mật khẩu") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                    
                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val config = ServerConfig(baseUrl = url.trim(), username = user.trim(), password = pass)
                            busy = true; error = null
                            scope.launch {
                                try {
                                    if (SubsonicRepository(config).ping()) onConnected(config)
                                    else error = "Sai tài khoản hoặc máy chủ"
                                } catch (e: Exception) {
                                    error = e.message ?: "Không kết nối được"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && user.isNotBlank() && url.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(top = 12.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            "Kết nối",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
