package com.drdisagree.teledrive.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.domain.model.DriveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drive avatar that cycles drives on a vertical swipe, the way mail apps flip
 * accounts. The outgoing picture slides toward the swipe and fades over a
 * bordered placeholder; the incoming one enters from the opposite edge once
 * the switch has landed, so the animation never shows the wrong drive. With a
 * single drive the picture just springs back. Taps pass through to the parent
 * untouched.
 */
@Composable
fun DriveAvatarSwitcher(
    channel: DriveChannel,
    canCycle: Boolean,
    onCycle: (Int) -> Unit,
    size: Dp = 48.dp,
    contentDescription: String? = null
) {
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val offset = remember { Animatable(0f) }
    var incoming by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(channel.chatId) {
        if (incoming != 0) {
            offset.snapTo(incoming * sizePx)
            incoming = 0
            offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .pointerInput(sizePx, canCycle) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offset.snapTo(
                                (offset.value + dragAmount)
                                    .coerceIn(-sizePx, sizePx)
                            )
                        }
                    },
                    onDragCancel = {
                        scope.launch { offset.animateTo(0f, spring()) }
                    },
                    onDragEnd = {
                        val travelled = offset.value
                        if (!canCycle || travelled.absoluteValue < sizePx * COMMIT_FRACTION) {
                            scope.launch { offset.animateTo(0f, spring()) }
                            return@detectVerticalDragGestures
                        }
                        val direction = if (travelled < 0) 1 else -1
                        scope.launch {
                            offset.animateTo(
                                -direction * sizePx,
                                tween(EXIT_MILLIS)
                            )
                            incoming = direction
                            onCycle(direction)
                            delay(SWITCH_TIMEOUT_MILLIS.milliseconds)
                            if (incoming != 0) {
                                incoming = 0
                                offset.animateTo(0f)
                            }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = offset.value
                alpha = 1f - (offset.value.absoluteValue / sizePx).coerceIn(0f, 1f)
            }
        ) {
            ChannelAvatar(
                channel = channel,
                size = size,
                contentDescription = contentDescription
            )
        }
    }
}

private const val COMMIT_FRACTION = 0.35f
private const val EXIT_MILLIS = 120
private const val SWITCH_TIMEOUT_MILLIS = 4_000L
