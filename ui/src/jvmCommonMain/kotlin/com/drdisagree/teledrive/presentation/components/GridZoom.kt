package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.drdisagree.teledrive.domain.model.ViewMode

const val MIN_GRID_COLUMNS = 2
const val MAX_GRID_COLUMNS = 6

data class GridZoomLevel(val viewMode: ViewMode, val gridSize: Int)

fun GridZoomLevel.zoomedIn(): GridZoomLevel = when {
    viewMode == ViewMode.LIST -> GridZoomLevel(ViewMode.GRID, MAX_GRID_COLUMNS)
    else -> GridZoomLevel(ViewMode.GRID, (gridSize - 1).coerceAtLeast(MIN_GRID_COLUMNS))
}

fun GridZoomLevel.zoomedOut(): GridZoomLevel = when {
    viewMode == ViewMode.LIST -> this
    gridSize >= MAX_GRID_COLUMNS -> GridZoomLevel(ViewMode.LIST, gridSize)
    else -> GridZoomLevel(ViewMode.GRID, gridSize + 1)
}

fun Modifier.pinchZoom(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier =
    wheelZoom(onZoomIn, onZoomOut).pointerInput(onZoomIn, onZoomOut) {
        awaitEachGesture {
            var accumulated = 1f
            var multitouch = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    multitouch = true
                    accumulated *= event.calculateZoom()
                    when {
                        accumulated >= STEP_THRESHOLD -> {
                            onZoomIn()
                            accumulated = 1f
                        }

                        accumulated <= 1f / STEP_THRESHOLD -> {
                            onZoomOut()
                            accumulated = 1f
                        }
                    }
                }
                if (multitouch) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

/** Ctrl plus mouse wheel steps the grid the way a pinch does. */
private fun Modifier.wheelZoom(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier =
    pointerInput(onZoomIn, onZoomOut) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                if (!event.keyboardModifiers.isCtrlPressed) continue
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                when {
                    delta < 0f -> onZoomIn()
                    delta > 0f -> onZoomOut()
                }
                event.changes.forEach { it.consume() }
            }
        }
    }

private const val STEP_THRESHOLD = 1.3f
