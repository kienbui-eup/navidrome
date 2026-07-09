package me.troly.nhac.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * A custom modifier extension that detects swipe/drag gestures in 4 directions
 * (Up, Down, Left, Right) and triggers corresponding callbacks.
 */
fun Modifier.swipeGestures(
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
): Modifier = this.pointerInput(Unit) {
    var totalX = 0f
    var totalY = 0f
    detectDragGestures(
        onDragStart = {
            totalX = 0f
            totalY = 0f
        },
        onDrag = { change, dragAmount ->
            change.consume()
            totalX += dragAmount.x
            totalY += dragAmount.y
        },
        onDragEnd = {
            val absX = abs(totalX)
            val absY = abs(totalY)
            val minDistance = 80f // Threshold in density-independent pixels / pointer units
            if (absX > absY) {
                if (absX > minDistance) {
                    if (totalX > 0) {
                        onSwipeRight?.invoke()
                    } else {
                        onSwipeLeft?.invoke()
                    }
                }
            } else {
                if (absY > minDistance) {
                    if (totalY > 0) {
                        onSwipeDown?.invoke()
                    } else {
                        onSwipeUp?.invoke()
                    }
                }
            }
        }
    )
}
