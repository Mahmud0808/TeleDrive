package com.drdisagree.teledrive.presentation.preview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Pinch and double-tap zoomable image. Pan is clamped so the image cannot be
 * dragged off screen, and touches stay unconsumed while the image sits at 1x
 * so a horizontal drag still reaches the pager.
 */
@Composable
fun ZoomableImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        val zoomedIn = scale.value > 1f
                        val target = if (zoomedIn) 1f else DOUBLE_TAP_SCALE
                        val focus = if (zoomedIn) {
                            Offset.Zero
                        } else {
                            clampOffset(
                                Offset(
                                    (viewport.width / 2f - tapOffset.x) * (target - 1f),
                                    (viewport.height / 2f - tapOffset.y) * (target - 1f)
                                ),
                                target,
                                viewport
                            )
                        }
                        scope.launch {
                            launch { scale.animateTo(target, tween(ZOOM_ANIMATION_MS)) }
                            launch { offset.animateTo(focus, tween(ZOOM_ANIMATION_MS)) }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (!pinching && scale.value <= 1f) continue

                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom == 1f && pan == Offset.Zero) continue

                        val target = (scale.value * zoom).coerceIn(1f, MAX_SCALE)
                        val moved = if (target > 1f) {
                            clampOffset(offset.value + pan, target, viewport)
                        } else {
                            Offset.Zero
                        }
                        scope.launch {
                            launch { scale.snapTo(target) }
                            launch { offset.snapTo(moved) }
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                }
        )
    }
}

/** Keeps the scaled image covering the viewport, so no empty edge shows. */
private fun clampOffset(target: Offset, scale: Float, viewport: IntSize): Offset {
    val maxX = viewport.width * (scale - 1f) / 2f
    val maxY = viewport.height * (scale - 1f) / 2f
    return Offset(
        x = target.x.coerceIn(-maxX, maxX),
        y = target.y.coerceIn(-maxY, maxY)
    )
}

private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val ZOOM_ANIMATION_MS = 250
