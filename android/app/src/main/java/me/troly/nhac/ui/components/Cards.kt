package me.troly.nhac.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.troly.nhac.data.subsonic.Album
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.data.subsonic.coverArtUrl
import me.troly.nhac.ui.LocalIsTv
import me.troly.nhac.ui.LocalRepo
import me.troly.nhac.ui.rememberInteractionSource
import me.troly.nhac.ui.tvFocusable

@Composable
fun AlbumCard(album: Album, repo: SubsonicRepository, onClick: () -> Unit) {
    val isTv = LocalIsTv.current
    val source = rememberInteractionSource()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val width = if (isTv) 180.dp else 156.dp
    Column(
        modifier = Modifier
            .width(width)
            .then(if (isTv) Modifier.tvFocusable(source) else Modifier.scale(scale))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        CoverImage(
            url = repo.config.coverArtUrl(album.coverArt, 400),
            contentDescription = album.name,
            corner = 12.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        album.artist?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A labeled horizontal shelf of albums — the core of the Home experience. */
@Composable
fun AlbumRow(title: String, albums: List<Album>, onAlbum: (Album) -> Unit) {
    val repo = LocalRepo.current
    if (albums.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album, repo) { onAlbum(album) }
            }
        }
    }
}
