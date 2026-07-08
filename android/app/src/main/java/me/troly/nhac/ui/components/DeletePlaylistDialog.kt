package me.troly.nhac.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Shared confirmation for deleting a playlist. */
@Composable
fun DeletePlaylistDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xoá playlist?") },
        text = { Text("Xoá \"$name\"? Không thể hoàn tác.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Xoá") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}
