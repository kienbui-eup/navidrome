package me.troly.nhac.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.components.CoverImage

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

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onExpand)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                CoverImage(
                    url = meta?.artworkUri?.toString(),
                    contentDescription = null,
                    corner = 8.dp,
                    modifier = Modifier.size(48.dp),
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        meta?.title?.toString() ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        meta?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { player.togglePlay() }) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Phát/Dừng", tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Bài sau",
                        tint = MaterialTheme.colorScheme.onBackground)
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
