package me.troly.nhac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Cover art with crossfade, rounded corners, a themed placeholder while loading,
 * and a music-note fallback on error/empty. Used everywhere for a consistent look.
 */
@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url == null) {
            Fallback()
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(280).build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = { Fallback() },
            )
        }
    }
}

@Composable
private fun Fallback() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
