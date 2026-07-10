package me.troly.nhac.ui

import androidx.compose.foundation.focusable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import me.troly.nhac.data.subsonic.SubsonicRepository
import me.troly.nhac.playback.PlayerConnection
import me.troly.nhac.playback.RecommendationManager

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.tween

val LocalIsTv = staticCompositionLocalOf { false }
val LocalPlayer = staticCompositionLocalOf<PlayerConnection> { error("PlayerConnection not provided") }
val LocalRepo = staticCompositionLocalOf<SubsonicRepository> { error("Repository not provided") }
val LocalRecManager = staticCompositionLocalOf<RecommendationManager> { error("RecommendationManager not provided") }

@Composable
fun ProvideAppEnv(
    isTv: Boolean,
    player: PlayerConnection,
    repo: SubsonicRepository,
    recManager: RecommendationManager,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(
    LocalIsTv provides isTv,
    LocalPlayer provides player,
    LocalRepo provides repo,
    LocalRecManager provides recManager,
    content = content,
)

/**
 * D-pad friendly focus visual: scales up and draws an accent border while focused.
 * Attach the returned interaction source to a focusable/clickable to drive it.
 */
@Composable
fun Modifier.tvFocusable(source: MutableInteractionSource): Modifier {
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 220, easing = EaseOutQuad),
        label = "focusScale"
    )
    return this
        .graphicsLayer { 
            scaleX = scale
            scaleY = scale
        }
        .focusable(interactionSource = source)
        .then(
            if (focused) Modifier
                .shadow(12.dp, RoundedCornerShape(10.dp))
                .border(2.dp, Color(0xFFC4BBA6), RoundedCornerShape(10.dp))
            else Modifier,
        )
}

@Composable
fun rememberInteractionSource() = remember { MutableInteractionSource() }


