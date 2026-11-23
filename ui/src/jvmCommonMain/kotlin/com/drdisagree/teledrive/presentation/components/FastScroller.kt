package com.drdisagree.teledrive.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.presentation.platform.LocalPlatformCapabilities
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FastScroller(
    state: LazyGridState,
    model: LineModel? = null,
    modifier: Modifier = Modifier,
    label: ((Int) -> String?)? = null
) {
    val target = remember(state) { ScrollerTarget.Grid(state) }
    FastScroller(target, model, modifier, label)
}

@Composable
fun FastScroller(
    state: LazyListState,
    model: LineModel? = null,
    modifier: Modifier = Modifier,
    label: ((Int) -> String?)? = null
) {
    val target = remember(state) { ScrollerTarget.Linear(state) }
    FastScroller(target, model, modifier, label)
}

@Composable
private fun FastScroller(
    target: ScrollerTarget,
    model: LineModel?,
    modifier: Modifier,
    label: ((Int) -> String?)?
) {
    val alwaysVisible = LocalPlatformCapabilities.current.usesPointerSelection
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbPx = with(density) { THUMB_HEIGHT.toPx() }
    val bubbleGapPx = with(density) { BUBBLE_GAP.toPx() }
    var trackPx by remember { mutableIntStateOf(0) }
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(alwaysVisible) }

    val heights = remember(target) { LineHeights() }
    val currentModel by rememberUpdatedState(
        model?.takeIf { it.totalItems == target.totalItems && it.lineCount > 0 }
    )
    val geometry by remember(target) {
        derivedStateOf {
            val lines = target.lines()
            val first = lines.firstOrNull() ?: return@derivedStateOf null
            val columns = lines.maxOf { it.itemCount }.coerceAtLeast(1)
            val layout = currentModel ?: LineModel.uniform(target.totalItems, columns)
            heights.record(layout, lines)
            val content = heights.total(layout) - target.spacing + target.padding
            val maxScroll = (content - target.viewport).coerceAtLeast(1f)
            val scrolled = heights.before(layout, layout.lineOf(first.firstIndex)) - first.top
            ScrollGeometry(layout, scrolled, maxScroll)
        }
    }

    val scrollable by remember(target) {
        derivedStateOf { target.canScrollForward || target.canScrollBackward }
    }
    val progress by remember(target) {
        derivedStateOf {
            when {
                !target.canScrollBackward -> 0f
                !target.canScrollForward -> 1f
                else -> geometry?.let { (it.scrolled / it.maxScroll).coerceIn(0f, 1f) } ?: 0f
            }
        }
    }

    LaunchedEffect(target.isScrollInProgress, dragging, alwaysVisible) {
        if (alwaysVisible || target.isScrollInProgress || dragging) {
            shown = true
        } else {
            delay(HIDE_DELAY_MS.milliseconds)
            shown = false
        }
    }
    val alpha by animateFloatAsState(if (shown && scrollable) 1f else 0f, label = "scroller")
    val active = alpha > 0f

    val travel = (trackPx - thumbPx).coerceAtLeast(1f)
    val moveTo by rememberUpdatedState { y: Float ->
        val fraction = ((y - thumbPx / 2) / travel).coerceIn(0f, 1f)
        dragFraction = fraction
        geometry?.let { current ->
            val (line, offset) = heights.lineAt(current.model, fraction * current.maxScroll)
            val index = current.model.startOf(line)
            scope.launch { target.scrollToItem(index, offset.roundToInt()) }
        }
        Unit
    }
    val trackWidth = if (alwaysVisible) FastScrollerGutter else TOUCH_TRACK_WIDTH
    val position = if (dragging) dragFraction else progress
    val bubble = if (dragging && label != null) {
        geometry?.let { current ->
            val pixel = dragFraction * (current.maxScroll + target.viewport - target.padding)
            val (line, _) = heights.lineAt(current.model, pixel)
            label(current.model.startOf(line))
        }
    } else {
        null
    }
    val haptics = LocalHapticFeedback.current
    var lastBubble by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bubble) {
        if (bubble != null && lastBubble != null && bubble != lastBubble) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        lastBubble = bubble
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(trackWidth)
            .onSizeChanged {
                trackPx = it.height
                trackWidthPx = it.width
            }
            .then(
                if (active) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { offset -> moveTo(offset.y) }
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    dragging = true
                                    moveTo(offset.y)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    moveTo(change.position.y)
                                }
                            )
                        }
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (position * travel).roundToInt()) }
                .padding(end = THUMB_END_PADDING)
                .size(
                    width = if (dragging) THUMB_WIDTH_ACTIVE else THUMB_WIDTH,
                    height = THUMB_HEIGHT
                )
                .alpha(alpha)
                .background(
                    color = if (dragging) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    shape = RoundedCornerShape(50)
                )
        )
        if (bubble != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            -(trackWidthPx + bubbleGapPx).roundToInt(),
                            (position * travel).roundToInt()
                        )
                    }
                    .wrapContentSize(align = Alignment.TopEnd, unbounded = true)
            ) {
                Text(
                    text = bubble,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private const val HIDE_DELAY_MS = 1_500L
private val TOUCH_TRACK_WIDTH = 28.dp
private val THUMB_END_PADDING = 4.dp
private val THUMB_WIDTH = 6.dp
private val THUMB_WIDTH_ACTIVE = 10.dp
private val THUMB_HEIGHT = 56.dp
private val BUBBLE_GAP = 4.dp
val FastScrollerGutter = 16.dp
