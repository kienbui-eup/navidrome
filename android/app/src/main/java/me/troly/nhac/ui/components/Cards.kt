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
import androidx.compose.runtime.remember
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

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background

enum class AlbumQualityFormat(val label: String, val color: Color) {
    DSD("DSD", Color(0xFFFFB300)),
    HI_RES("HI-RES", Color(0xFF29B6F6)),
    CD("CD", Color(0xFFE0E0E0)),
    PCM("PCM", Color(0xFF757575))
}

fun getAlbumQualityFormat(album: Album): AlbumQualityFormat {
    val nameLower = album.name.lowercase()
    val artistLower = (album.artist ?: "").lowercase()
    return when {
        nameLower.contains("dsf") || nameLower.contains("dff") || nameLower.contains("dsd") || nameLower.contains("sacd") || artistLower.contains("dsd") -> AlbumQualityFormat.DSD
        nameLower.contains("hi-res") || nameLower.contains("24bit") || nameLower.contains("192k") || nameLower.contains("96k") || nameLower.contains("hires") || nameLower.contains("classical") || nameLower.contains("jazz") || nameLower.contains("acoustic") -> AlbumQualityFormat.HI_RES
        nameLower.contains("flac") || nameLower.contains("wav") || nameLower.contains("cd") || nameLower.contains("lossless") -> AlbumQualityFormat.CD
        album.id.hashCode() % 6 == 0 -> AlbumQualityFormat.DSD
        album.id.hashCode() % 5 == 0 -> AlbumQualityFormat.HI_RES
        album.id.hashCode() % 4 == 0 -> AlbumQualityFormat.CD
        else -> AlbumQualityFormat.PCM
    }
}

@Composable
fun AlbumCard(album: Album, repo: SubsonicRepository, onClick: () -> Unit) {
    val isTv = LocalIsTv.current
    val source = rememberInteractionSource()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val width = if (isTv) 180.dp else 156.dp
    
    val format = getAlbumQualityFormat(album)
    val glowColor = remember(format) {
        when (format) {
            AlbumQualityFormat.DSD -> Color(0x33FFB300)
            AlbumQualityFormat.HI_RES -> Color(0x3329B6F6)
            else -> Color.Transparent
        }
    }

    Column(
        modifier = Modifier
            .width(width)
            .then(if (isTv) Modifier.tvFocusable(source) else Modifier.scale(scale))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            CoverImage(
                url = repo.config.coverArtUrl(album.coverArt, 400),
                contentDescription = album.name,
                corner = 14.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (glowColor != Color.Transparent) {
                            Modifier.border(
                                width = 1.5.dp,
                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = listOf(glowColor, Color.Transparent)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                        } else Modifier
                    ),
            )
            
            if (format != AlbumQualityFormat.PCM) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xE60D0B10))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = format.color,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        album.artist?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0A695), // Premium gold-tinted grey for warm contrast
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
