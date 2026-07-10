package me.troly.nhac.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.components.CoverImage
import me.troly.nhac.playback.AudioDeviceHelper
import me.troly.nhac.ui.components.swipeGestures

@Composable
fun MiniPlayer(onExpand: () -> Unit) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    if (!state.hasMedia) return
    val meta = state.current

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) { player.tick(); delay(500) }
    }
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isVeryNarrow = configuration.screenWidthDp < 340

    // Dynamic activeDevice state updating in real-time (every 1.5 seconds)
    val context = androidx.compose.ui.platform.LocalContext.current
    var activeDevice by remember { mutableStateOf(AudioDeviceHelper.getActiveDeviceDetails(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDevice = AudioDeviceHelper.getActiveDeviceDetails(context)
            delay(1500)
        }
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF221E19), // top slightly lighter warm charcoal-gold
                        Color(0xFF14120F)  // bottom deeper warm near-black
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .swipeGestures(
                    onSwipeUp = onExpand,
                    onSwipeLeft = { player.next() },
                    onSwipeRight = { player.previous() }
                )
                .clickable(onClick = onExpand)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (isVeryNarrow) 6.dp else 8.dp),
            ) {
                CoverImage(
                    url = meta?.artworkUri?.toString(),
                    contentDescription = null,
                    corner = 8.dp,
                    modifier = Modifier.size(if (isVeryNarrow) 42.dp else 48.dp),
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        meta?.title?.toString() ?: "",
                        style = if (isVeryNarrow) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        meta?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    
                    // Compact Signal Path & Connected Device
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
                            activeDevice.isLossless && isDsdOrHighRes -> Color(0xFF00E676)
                            activeDevice.isHiResCapable -> Color(0xFF29B6F6)
                            else -> Color(0xFFFFB300)
                        }
                    }
                    
                    val summaryText = remember(suffix, bitDepth, samplingRate, bitRate, activeDevice, isVeryNarrow) {
                        val src = if (!suffix.isNullOrBlank()) {
                            val fmt = suffix.uppercase()
                            if (isVeryNarrow) {
                                fmt
                            } else {
                                val spec = if (bitDepth > 0 && samplingRate > 0) {
                                    "${bitDepth}b/${samplingRate / 1000}k"
                                } else if (bitRate > 0) {
                                    "${bitRate}k"
                                } else ""
                                if (spec.isNotEmpty()) "$fmt $spec" else fmt
                            }
                        } else "Audio"
                        
                        val isTranscoded = me.troly.nhac.data.subsonic.isServerTranscodeSuffix(suffix)
                        val transcodeStr = if (isTranscoded && !isVeryNarrow) " ➔ FLAC" else ""
                        
                        val shortDeviceName = when {
                            activeDevice.typeLabel == "USB DAC" -> "DAC"
                            activeDevice.name.contains("Buds2 Pro", ignoreCase = true) -> "Buds"
                            activeDevice.name.contains("UP5", ignoreCase = true) -> "UP5"
                            else -> if (isVeryNarrow) activeDevice.typeLabel.take(6) else activeDevice.typeLabel
                        }
                        
                        "$src$transcodeStr ➔ $shortDeviceName"
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ledColor)
                        )
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.labelSmall,
                            color = ledColor.copy(alpha = 0.85f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { player.togglePlay() }) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Phát/Dừng", tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (!isVeryNarrow) {
                    IconButton(onClick = { player.next() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Bài sau",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}
