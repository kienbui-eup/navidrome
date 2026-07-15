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

/**
 * A highly optimized edge-swipe back gesture modifier.
 * Allows swiping from the left edge of the screen to go back,
 * completely bypasses and does not interfere with vertical scrolling list containers like LazyColumn.
 */
fun Modifier.swipeBackGesture(
    edgeWidthPx: Float = 150f,
    minDragDistancePx: Float = 180f,
    onBack: () -> Unit
): Modifier = this.pointerInput(Unit) {
    var startX = 0f
    var startY = 0f
    var totalDragX = 0f
    var totalDragY = 0f
    var isGestureActive = false
    
    detectDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            startY = offset.y
            totalDragX = 0f
            totalDragY = 0f
            // Only trigger if starting from the left edge of the screen
            isGestureActive = startX <= edgeWidthPx
        },
        onDrag = { change, dragAmount ->
            if (isGestureActive) {
                totalDragX += dragAmount.x
                totalDragY += dragAmount.y
                
                // Intelligently handle scroll lists: if vertical dragging is stronger than horizontal,
                // deactivate the gesture to allow normal vertical scroll of the list containers.
                if (abs(totalDragY) > abs(totalDragX) && abs(totalDragY) > 50f) {
                    isGestureActive = false
                } else {
                    change.consume()
                }
            }
        },
        onDragEnd = {
            if (isGestureActive && totalDragX > minDragDistancePx) {
                onBack()
            }
            isGestureActive = false
        },
        onDragCancel = {
            isGestureActive = false
        }
    )
}

