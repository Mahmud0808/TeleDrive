package com.drdisagree.teledrive.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.drdisagree.teledrive.domain.model.ViewMode
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom

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
    pointerInput(onZoomIn, onZoomOut) {
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

private const val STEP_THRESHOLD = 1.3f
