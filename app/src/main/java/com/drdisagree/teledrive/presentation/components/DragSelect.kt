package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull

/**
 * Long-press-and-drag range selection. The press is handled by the item, which
 * selects itself and becomes the anchor; dragging away extends the selection
 * over everything between the anchor and the finger. Holding near an edge keeps
 * the list scrolling, so a range can run past the viewport. Reported indices
 * are item indices, which the caller maps to its own content.
 */
@Composable
fun rememberDragSelect(
    gridState: LazyGridState,
    onStart: () -> Unit,
    onRange: (IntRange) -> Unit,
    onEnd: () -> Unit,
    edgeSize: Dp = EDGE_SIZE,
    scrollPerFrame: Dp = SCROLL_PER_FRAME
): Modifier = dragSelect(
    scrollable = gridState,
    indexAt = { offset ->
        gridState.layoutInfo.visibleItemsInfo.fastFirstOrNull { item ->
            offset.x >= item.offset.x &&
                    offset.x <= item.offset.x + item.size.width &&
                    offset.y >= item.offset.y &&
                    offset.y <= item.offset.y + item.size.height
        }?.index
    },
    onStart = onStart,
    onRange = onRange,
    onEnd = onEnd,
    edgeSize = edgeSize,
    scrollPerFrame = scrollPerFrame
)

@Composable
fun rememberDragSelect(
    listState: LazyListState,
    onStart: () -> Unit,
    onRange: (IntRange) -> Unit,
    onEnd: () -> Unit,
    edgeSize: Dp = EDGE_SIZE,
    scrollPerFrame: Dp = SCROLL_PER_FRAME
): Modifier = dragSelect(
    scrollable = listState,
    indexAt = { offset ->
        listState.layoutInfo.visibleItemsInfo.fastFirstOrNull { item ->
            offset.y >= item.offset && offset.y <= item.offset + item.size
        }?.index
    },
    onStart = onStart,
    onRange = onRange,
    onEnd = onEnd,
    edgeSize = edgeSize,
    scrollPerFrame = scrollPerFrame
)

@Composable
private fun dragSelect(
    scrollable: ScrollableState,
    indexAt: (Offset) -> Int?,
    onStart: () -> Unit,
    onRange: (IntRange) -> Unit,
    onEnd: () -> Unit,
    edgeSize: Dp,
    scrollPerFrame: Dp
): Modifier {
    val density = LocalDensity.current
    val edgePx = with(density) { edgeSize.toPx() }
    val maxScrollPx = with(density) { scrollPerFrame.toPx() }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val scrolling = autoScroll != 0f

    val currentIndexAt by rememberUpdatedState(indexAt)
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnRange by rememberUpdatedState(onRange)
    val currentOnEnd by rememberUpdatedState(onEnd)

    LaunchedEffect(scrolling, scrollable) {
        while (scrolling) {
            withFrameNanos { }
            scrollable.scrollBy(autoScroll)
        }
    }

    return Modifier.pointerInput(scrollable) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!awaitLongPress(down)) return@awaitEachGesture

            var anchor = currentIndexAt(down.position)
            currentOnStart()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                val current = currentIndexAt(change.position)
                if (current != null) {
                    val start = anchor ?: current.also { anchor = it }
                    currentOnRange(minOf(start, current)..maxOf(start, current))
                }
                autoScroll = edgeScroll(change.position.y, size.height, edgePx, maxScrollPx)
            }
            autoScroll = 0f
            currentOnEnd()
        }
    }
}

/**
 * Waits out the long-press timeout without consuming anything, so the item
 * under the finger still gets its own long press and becomes the anchor. A
 * move past touch slop means the gesture is a scroll, not a selection.
 */
private suspend fun AwaitPointerEventScope.awaitLongPress(down: PointerInputChange): Boolean =
    try {
        withTimeout(viewConfiguration.longPressTimeoutMillis) {
            var held = true
            while (held) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.fastFirstOrNull { it.id == down.id }
                held = change != null && change.pressed &&
                        (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
            }
            false
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        true
    }

private fun edgeScroll(y: Float, viewportHeight: Int, edgePx: Float, maxScrollPx: Float): Float =
    when {
        y < edgePx -> -((edgePx - y) / edgePx).coerceIn(0f, 1f) * maxScrollPx
        y > viewportHeight - edgePx ->
            ((y - (viewportHeight - edgePx)) / edgePx).coerceIn(0f, 1f) * maxScrollPx

        else -> 0f
    }

private val EDGE_SIZE = 88.dp
private val SCROLL_PER_FRAME = 10.dp
