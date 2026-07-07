package me.troly.nhac.ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

        Column(Modifier.fillMaxSize().padding(24.dp)) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Đóng",
                    tint = Color.White)
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

            // Audio Quality Badges & Visualizer Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val isDac = remember(context) { isUsbDacConnected(context) }
                
                val extras = meta?.extras
                val suffix = extras?.getString("suffix")
                val bitRate = extras?.getInt("bitRate") ?: 0
                val bitDepth = extras?.getInt("bitDepth") ?: 0
                val samplingRate = extras?.getInt("samplingRate") ?: 0
                
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
                
                // 4. Bit-Perfect USB Badge
                if (isDac) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "Bit-Perfect",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
    }
}

private fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())
