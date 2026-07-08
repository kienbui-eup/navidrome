package me.troly.nhac.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * A single song line. Optional [onAdd] shows a trailing quick-add (+) button;
 * [onLongPress] enters bulk selection. In [selectionMode] the leading number is
 * replaced by a check indicator and [onClick] should toggle [selected].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onAdd: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val isTv = LocalIsTv.current
    val source = rememberInteractionSource()
    val accent = MaterialTheme.colorScheme.primary
    val clickMod =
        if (onLongPress != null)
            Modifier.combinedClickable(interactionSource = source, indication = null,
                onClick = onClick, onLongClick = onLongPress)
        else
            Modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isTv) Modifier.tvFocusable(source) else Modifier)
            .then(clickMod)
            .then(if (selected) Modifier.background(accent.copy(alpha = 0.14f)) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (isTv) 12.dp else 8.dp),
    ) {
        if (selectionMode) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
        }
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
        if (onAdd != null && !selectionMode) {
            IconButton(onClick = onAdd, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Thêm vào playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onDelete != null && !selectionMode) {
            IconButton(onClick = onDelete, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Xóa bài hát",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

fun formatDuration(seconds: Int?): String {
    val s = seconds ?: return ""
    return "%d:%02d".format(s / 60, s % 60)
}
