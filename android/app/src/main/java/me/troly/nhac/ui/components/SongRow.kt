package me.troly.nhac.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.troly.nhac.data.subsonic.Song
import me.troly.nhac.ui.LocalIsTv
import me.troly.nhac.ui.rememberInteractionSource
import me.troly.nhac.ui.tvFocusable

@Composable
fun SongRow(song: Song, index: Int, isCurrent: Boolean, onClick: () -> Unit) {
    val isTv = LocalIsTv.current
    val source = rememberInteractionSource()
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isTv) Modifier.tvFocusable(source) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (isTv) 12.dp else 8.dp),
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            song.artist?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

fun formatDuration(seconds: Int?): String {
    val s = seconds ?: return ""
    return "%d:%02d".format(s / 60, s % 60)
}
