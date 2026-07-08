package me.troly.nhac.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.Playlist
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.LocalRepo

/**
 * Bottom sheet that adds [songIds] to a playlist. Lists existing playlists and offers
 * a "Tạo playlist mới" row (create + seed in one call). Used for single quick-add,
 * whole-album add, and bulk-selection add. Shows a Toast and dismisses on completion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(songIds: List<String>, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playlists = runCatching { repo.playlists() }.getOrDefault(emptyList())
    }

    // Keeps the sheet mounted (so [scope] stays alive) until the call finishes, then closes.
    fun run(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = runCatching { block() }.isSuccess
            Toast.makeText(
                context,
                if (ok) label else "Không thêm được, thử lại",
                Toast.LENGTH_SHORT,
            ).show()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Thêm ${songIds.size} bài vào playlist",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !busy) { showCreate = true }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).background(
                    MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Text("Tạo playlist mới", style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        when {
            playlists == null -> Box(
                Modifier.fillMaxWidth().padding(32.dp), Alignment.Center,
            ) { CircularProgressIndicator() }
            playlists!!.isEmpty() -> Text(
                "Chưa có playlist nào — tạo mới ở trên",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
                items(playlists!!, key = { it.id }) { pl ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) {
                            run("Đã thêm vào ${pl.name}") { repo.addToPlaylist(pl.id, songIds) }
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = repo.config.coverArtUrl(pl.coverArt, 120),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold, maxLines = 1)
                            pl.songCount?.let {
                                Text("$it bài", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                showCreate = false
                run("Đã tạo playlist \"$name\"") { repo.createPlaylist(name, songIds) }
            },
        )
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo playlist mới") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Tên playlist") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Tạo")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}
