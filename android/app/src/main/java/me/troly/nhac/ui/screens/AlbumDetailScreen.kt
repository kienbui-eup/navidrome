package me.troly.nhac.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.troly.nhac.data.subsonic.Album
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.LocalPlayer
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.components.AddToPlaylistSheet
import me.troly.nhac.ui.components.CoverImage
import me.troly.nhac.ui.components.SongRow

import me.troly.nhac.ui.LocalIsTv
import me.troly.nhac.ui.rememberInteractionSource
import me.troly.nhac.ui.tvFocusable

class AlbumViewModel(private val repo: SubsonicRepository, private val albumId: String) : ViewModel() {
    private val _album = MutableStateFlow<Album?>(null)
    val album = _album.asStateFlow()
    init { 
        refresh() 
        viewModelScope.launch {
            repo.refreshEvent.collect {
                refresh()
            }
        }
    }
    fun refresh() { viewModelScope.launch { runCatching { _album.value = repo.album(albumId) } } }
}

@Composable
fun AlbumQualityBadge(songs: List<me.troly.nhac.data.subsonic.Song>, modifier: Modifier = Modifier) {
    val isDsd = songs.any { s -> s.suffix?.lowercase() in listOf("dsf", "dff", "diff") }
    val isHiRes = songs.any { s -> (s.bitDepth ?: 16) > 16 || (s.samplingRate ?: 44100) > 48000 }
    val isLossless = songs.any { s -> s.suffix?.lowercase() in listOf("flac", "alac", "wav", "dsf", "dff", "diff") }

    val (badgeText, badgeColors, ledColor) = when {
        isDsd -> Triple("DSD / SACD", listOf(Color(0xFFFFD700), Color(0xFFC5A059)), Color(0xFFFFD700))
        isHiRes -> Triple("Hi-Res LOSSLESS", listOf(Color(0xFF00E5FF), Color(0xFF00A8BC)), Color(0xFF00E5FF))
        isLossless -> Triple("CD LOSSLESS", listOf(Color(0xFFDCDCDC), Color(0xFF8C8C8C)), Color(0xFFB0C4DE))
        else -> Triple("STANDARD PCM", listOf(Color(0xFF9E9E9E), Color(0xFF6E6E6E)), Color(0xFF6E6E6E))
    }

    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(badgeColors),
                shape = RoundedCornerShape(6.dp)
            )
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(ledColor, CircleShape)
                    .shadow(elevation = 4.dp, shape = CircleShape, spotColor = ledColor)
            )
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: String, 
    onBack: () -> Unit, 
    onNowPlaying: () -> Unit,
    onArtistClick: ((String) -> Unit)? = null
) {
    val repo = LocalRepo.current
    val player = LocalPlayer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: AlbumViewModel = viewModel(key = "album-$albumId") { AlbumViewModel(repo, albumId) }
    val album by vm.album.collectAsState()
    val playState by player.state.collectAsState()

    var pendingAdd by remember { mutableStateOf<List<String>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val selectionMode = selected.isNotEmpty()

    var isAdmin by remember { mutableStateOf(false) }
    var showDeleteAlbumDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<me.troly.nhac.data.subsonic.Song?>(null) }

    val isTv = LocalIsTv.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = isTv || configuration.screenWidthDp >= 600

    LaunchedEffect(repo) {
        isAdmin = repo.checkAdminStatus()
    }

    // In selection mode, Back clears the selection instead of leaving the screen.
    BackHandler(enabled = selectionMode) { selected = emptySet() }
    BackHandler(enabled = !selectionMode, onBack = onBack)

    if (album == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val a = album!!
    val coverUrl = repo.config.coverArtUrl(a.coverArt, 600)
    val toggle = { id: String -> selected = if (id in selected) selected - id else selected + id }

    val totalSecs = remember(a.song) { a.song.fold(0) { acc, s -> acc + (s.duration ?: 0) } }
    val durationText = remember(totalSecs) {
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        "${mins} phút ${secs} giây"
    }

    Box(Modifier.fillMaxSize()) {
        // Ambient Fluid moving artwork background across the entire screen
        me.troly.nhac.ui.player.AnimatedAmbientBackground(artworkUri = coverUrl, modifier = Modifier.matchParentSize())

        if (isWideScreen) {
            // Dual-Pane Layout for Widescreen, Tablets, and TVs
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Panel: Responsive album visual details & actions
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .widthIn(min = 300.dp, max = 360.dp)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.01f))
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Actions Header (Fixed at the top)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val backSource = rememberInteractionSource()
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .then(if (isTv) Modifier.tvFocusable(backSource) else Modifier)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = Color.White)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isAdmin) {
                                val deleteSource = rememberInteractionSource()
                                IconButton(
                                    onClick = { showDeleteAlbumDialog = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .then(if (isTv) Modifier.tvFocusable(deleteSource) else Modifier)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, "Xoá album", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            val playlistSource = rememberInteractionSource()
                            IconButton(
                                onClick = { pendingAdd = a.song.map { it.id } },
                                modifier = Modifier
                                    .size(40.dp)
                                    .then(if (isTv) Modifier.tvFocusable(playlistSource) else Modifier)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Thêm vào playlist", tint = Color.White)
                            }
                        }
                    }

                    // Middle content scrollable block: Cover art & metadata (Scrollable when height is restricted, e.g. Fold 5)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Floating cover art with spot glow shadow
                        CoverImage(
                            url = coverUrl, contentDescription = a.name, corner = 20.dp,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .shadow(24.dp, RoundedCornerShape(20.dp))
                        )

                        Text(
                            text = a.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Interactive clickable Artist name
                        if (a.artist != null) {
                            val artistSource = rememberInteractionSource()
                            Text(
                                text = a.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC4BBA6),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(
                                        interactionSource = artistSource,
                                        indication = androidx.compose.foundation.LocalIndication.current,
                                        onClick = {
                                            a.artistId?.let { onArtistClick?.invoke(it) }
                                        }
                                    )
                                    .then(if (isTv) Modifier.tvFocusable(artistSource) else Modifier)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Format indicator badge
                        AlbumQualityBadge(songs = a.song, modifier = Modifier.padding(top = 8.dp))

                        Text(
                            text = listOfNotNull(
                                a.year?.toString(),
                                if (a.song.isNotEmpty()) "${a.song.size} bài • $durationText" else a.songCount?.let { "$it bài" }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // Action buttons (Fixed at the bottom)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val playSource = rememberInteractionSource()
                        Button(
                            onClick = { player.play(a.song, 0); onNowPlaying() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .then(if (isTv) Modifier.tvFocusable(playSource) else Modifier)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(
                                text = "Phát",
                                modifier = Modifier.padding(start = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val shuffleSource = rememberInteractionSource()
                        OutlinedButton(
                            onClick = { player.play(a.song.shuffled(), 0); onNowPlaying() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .then(if (isTv) Modifier.tvFocusable(shuffleSource) else Modifier)
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null)
                            Text(
                                text = "Xáo trộn",
                                modifier = Modifier.padding(start = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right Panel: Scrollable Tracklist
                LazyColumn(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = 16.dp, end = 16.dp, bottom = if (selectionMode) 160.dp else 88.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(a.song, key = { _, s -> s.id }) { index, song ->
                        SongRow(
                            song = song, index = index,
                            isCurrent = playState.current?.title?.toString() == song.title,
                            onClick = {
                                if (selectionMode) toggle(song.id)
                                else { player.play(a.song, index); onNowPlaying() }
                            },
                            onAdd = { pendingAdd = listOf(song.id) },
                            onDelete = if (isAdmin) { { songToDelete = song } } else null,
                            onLongPress = { toggle(song.id) },
                            selectionMode = selectionMode,
                            selected = song.id in selected,
                        )
                    }
                }
            }
        } else {
            // Standard Mobile Portrait Layout
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (selectionMode) 160.dp else 88.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val backSource = rememberInteractionSource()
                            IconButton(
                                onClick = onBack, 
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(48.dp)
                                    .then(if (isTv) Modifier.tvFocusable(backSource) else Modifier)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = Color.White,
                                    modifier = Modifier.size(28.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAdmin) {
                                    val deleteSource = rememberInteractionSource()
                                    IconButton(
                                        onClick = { showDeleteAlbumDialog = true },
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(48.dp)
                                            .then(if (isTv) Modifier.tvFocusable(deleteSource) else Modifier),
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline, "Xoá album",
                                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                val playlistSource = rememberInteractionSource()
                                IconButton(
                                    onClick = { pendingAdd = a.song.map { it.id } },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(48.dp)
                                        .then(if (isTv) Modifier.tvFocusable(playlistSource) else Modifier),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Thêm album vào playlist",
                                        tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .shadow(16.dp, RoundedCornerShape(20.dp))
                                .background(
                                    color = Color.White.copy(alpha = 0.04f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.01f))
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(vertical = 20.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CoverImage(
                                url = coverUrl, contentDescription = a.name, corner = 16.dp,
                                modifier = Modifier.size(216.dp).shadow(20.dp, RoundedCornerShape(16.dp)),
                            )
                            Text(a.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                color = Color.White, modifier = Modifier.padding(top = 14.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            
                            // Clickable Artist name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                if (a.artist != null) {
                                    val artistSource = rememberInteractionSource()
                                    Text(
                                        text = a.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFC4BBA6),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable(
                                                interactionSource = artistSource,
                                                indication = androidx.compose.foundation.LocalIndication.current,
                                                onClick = {
                                                    a.artistId?.let { onArtistClick?.invoke(it) }
                                                }
                                            )
                                            .padding(horizontal = 4.dp)
                                    )
                                    Text(" • ", color = Color(0xFFC4BBA6).copy(alpha = 0.5f))
                                }
                                val extraDetails = listOfNotNull(
                                    a.year?.toString(),
                                    if (a.song.isNotEmpty()) "${a.song.size} bài • $durationText" else a.songCount?.let { "$it bài" }
                                ).joinToString(" • ")
                                Text(extraDetails, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC4BBA6).copy(alpha = 0.7f))
                            }

                            // Format Quality Badge
                            AlbumQualityBadge(songs = a.song, modifier = Modifier.padding(top = 8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val playSource = rememberInteractionSource()
                                Button(
                                    onClick = { player.play(a.song, 0); onNowPlaying() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (isTv) Modifier.tvFocusable(playSource) else Modifier)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Text(
                                        text = "Phát",
                                        modifier = Modifier.padding(start = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val shuffleSource = rememberInteractionSource()
                                OutlinedButton(
                                    onClick = { player.play(a.song.shuffled(), 0); onNowPlaying() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (isTv) Modifier.tvFocusable(shuffleSource) else Modifier)
                                ) {
                                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                                    Text(
                                        text = "Ngẫu nhiên",
                                        modifier = Modifier.padding(start = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                itemsIndexed(a.song, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song, index = index,
                        isCurrent = playState.current?.title?.toString() == song.title,
                        onClick = {
                            if (selectionMode) toggle(song.id)
                            else { player.play(a.song, index); onNowPlaying() }
                        },
                        onAdd = { pendingAdd = listOf(song.id) },
                        onDelete = if (isAdmin) { { songToDelete = song } } else null,
                        onLongPress = { toggle(song.id) },
                        selectionMode = selectionMode,
                        selected = song.id in selected,
                    )
                }
            }
        }

        // Selection Bottom Bar
        if (selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Filled.Close, "Bỏ chọn", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("Đã chọn ${selected.size}", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Button(onClick = { pendingAdd = selected.toList(); selected = emptySet() }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                        Text("Thêm vào playlist", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }

    if (showDeleteAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAlbumDialog = false },
            title = { Text("Xoá Album vĩnh viễn?") },
            text = { Text("Bạn có chắc chắn muốn xoá vĩnh viễn album \"${a.name}\"? Thao tác này sẽ xoá toàn bộ tệp tin nhạc của album này trên đĩa cứng máy chủ và không thể khôi phục.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAlbumDialog = false
                        scope.launch {
                            try {
                                val success = repo.deleteAlbum(albumId)
                                if (success) {
                                    Toast.makeText(context, "Đã xoá album thành công", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, "Xoá album thất bại", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xoá vĩnh viễn")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlbumDialog = false }) {
                    Text("Huỷ")
                }
            }
        )
    }

    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("Xoá bài hát vĩnh viễn?") },
            text = { Text("Bạn có chắc chắn muốn xoá vĩnh viễn bài hát \"${song.title}\"? Thao tác này sẽ xoá tệp tin nhạc trên đĩa cứng máy chủ và không thể khôi phục.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = songToDelete
                        songToDelete = null
                        if (toDelete != null) {
                            scope.launch {
                                try {
                                    val success = repo.deleteSong(toDelete.id)
                                    if (success) {
                                        Toast.makeText(context, "Đã xoá bài hát thành công", Toast.LENGTH_SHORT).show()
                                        vm.refresh()
                                    } else {
                                        Toast.makeText(context, "Xoá bài hát thất bại", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xoá vĩnh viễn")
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text("Huỷ")
                }
            }
        )
    }

    pendingAdd?.let { ids ->
        AddToPlaylistSheet(songIds = ids, onDismiss = { pendingAdd = null })
    }
}

