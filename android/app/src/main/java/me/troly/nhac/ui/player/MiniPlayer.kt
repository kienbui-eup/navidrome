package me.troly.nhac.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.components.CoverImage
import me.troly.nhac.playback.AudioDeviceHelper
import me.troly.nhac.ui.components.swipeGestures

/**
 * A beautiful live-animated vertical 4-bar Audio Wave Visualizer.
 * Bounces organically when music is playing, and gently collapses to resting state when paused.
 */
@Composable
fun AudioWaveVisualizer(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_wave")
    
    // Unique cycle durations for each bar to avoid synthetic repetition
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val bar4Height by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(bar1Height, bar2Height, bar3Height, bar4Height)
        for (i in 0..3) {
            val targetPercent = if (isPlaying) heights[i] else 0.15f
            val smoothPercent by animateFloatAsState(
                targetValue = targetPercent,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "smooth_bar_$i"
            )
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(smoothPercent)
                    .clip(RoundedCornerShape(1.2.dp))
                    .background(color)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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

    // Dynamic activeDevice state updating in real-time
    val context = androidx.compose.ui.platform.LocalContext.current
    var activeDevice by remember { mutableStateOf(AudioDeviceHelper.getActiveDeviceDetails(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            activeDevice = AudioDeviceHelper.getActiveDeviceDetails(context)
            delay(1500)
        }
    }

    // Floating Glassmorphic Capsule Layout wrapping the MiniPlayer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xF01A1813), // premium warm semi-translucent dark grey
                        Color(0xF00F0D0B)  // matching bottom deep background
                    )
                )
            )
            .border(BorderStroke(1.dp, Color(0x22C4BBA6)), RoundedCornerShape(16.dp))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = if (isVeryNarrow) 6.dp else 10.dp),
            ) {
                // Interactive cover art expanding slightly when active
                val coverScale by animateFloatAsState(
                    targetValue = if (state.isPlaying) 1.05f else 0.95f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "cover_scale"
                )
                Box(
                    modifier = Modifier
                        .size(if (isVeryNarrow) 42.dp else 48.dp)
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color(0x18FFFFFF)), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CoverImage(
                        url = meta?.artworkUri?.toString(),
                        contentDescription = null,
                        corner = 8.dp,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight()
                    )
                }
                
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        meta?.title?.toString() ?: "",
                        style = if (isVeryNarrow) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 1500
                        )
                    )
                    Text(
                        meta?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC4BBA6),
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
                    val targetLedColor = remember(activeDevice, isDsdOrHighRes) {
                        when {
                            activeDevice.isLossless && isDsdOrHighRes -> Color(0xFF00E676)
                            activeDevice.isHiResCapable -> Color(0xFF29B6F6)
                            else -> Color(0xFFFFB300)
                        }
                    }
                    val ledColor by animateColorAsState(targetLedColor, label = "led_color")
                    
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
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        // Interactive Live Audio Wave Visualizer in place of the static dot
                        AudioWaveVisualizer(
                            isPlaying = state.isPlaying,
                            color = ledColor,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .height(11.dp)
                        )
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.labelSmall,
                            color = ledColor.copy(alpha = 0.85f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                IconButton(
                    onClick = { player.togglePlay() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Phát/Dừng",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (!isVeryNarrow) {
                    IconButton(
                        onClick = { player.next() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Bài sau",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            
            // Ultra-sleek, fine progress bar at the very bottom edge of the capsule card
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0x15FFFFFF),
            )
        }
    }
}
