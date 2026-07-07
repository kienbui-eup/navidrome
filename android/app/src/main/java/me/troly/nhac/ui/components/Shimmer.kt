package me.troly.nhac.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Animated shimmer background for loading placeholders. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "shimmerX",
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFF1A1712), Color(0xFF2E281E), Color(0xFF1A1712)),
        start = Offset(x, 0f), end = Offset(x + 300f, 300f),
    )
    return this.background(brush)
}

@Composable
fun SkeletonHome() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(240.dp).padding(16.dp).clip(RoundedCornerShape(16.dp)).shimmer())
        repeat(3) {
            Column(Modifier.padding(top = 16.dp)) {
                Box(Modifier.padding(start = 16.dp).size(width = 160.dp, height = 20.dp)
                    .clip(RoundedCornerShape(6.dp)).shimmer())
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) {
                        Box(Modifier.size(140.dp).clip(RoundedCornerShape(10.dp)).shimmer())
                    }
                }
            }
        }
    }
}
