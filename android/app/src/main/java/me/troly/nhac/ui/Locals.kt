package me.troly.nhac.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.playback.PlayerConnection

val LocalIsTv = staticCompositionLocalOf { false }
val LocalPlayer = staticCompositionLocalOf<PlayerConnection> { error("PlayerConnection not provided") }
val LocalRepo = staticCompositionLocalOf<SubsonicRepository> { error("Repository not provided") }

@Composable
fun ProvideAppEnv(
    isTv: Boolean,
    player: PlayerConnection,
    repo: SubsonicRepository,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(
    LocalIsTv provides isTv,
    LocalPlayer provides player,
    LocalRepo provides repo,
    content = content,
)

/**
 * D-pad friendly focus visual: scales up and draws an accent border while focused.
 * Attach the returned interaction source to a focusable/clickable to drive it.
 */
@Composable
fun Modifier.tvFocusable(source: MutableInteractionSource): Modifier {
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(
            if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            else Modifier,
        )
}

@Composable
fun rememberInteractionSource() = remember { MutableInteractionSource() }
