package com.drdisagree.teledrive.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

enum class ConnectionIndicator { CONNECTED, WORKING, OFFLINE }

/**
 * Status light for the Telegram connection. It keeps pulsing while the client
 * is still working so a slow connect never reads as a frozen screen.
 */
@Composable
fun ConnectionDot(
    status: ConnectionIndicator,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        ConnectionIndicator.CONNECTED -> CONNECTED_COLOR
        ConnectionIndicator.WORKING -> WORKING_COLOR
        ConnectionIndicator.OFFLINE -> MaterialTheme.colorScheme.error
    }
    val pulsing = status != ConnectionIndicator.CONNECTED
    val transition = rememberInfiniteTransition(label = "connectionPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    Box(
        modifier = modifier.size(DOT_AREA),
        contentAlignment = Alignment.Center
    ) {
        if (pulsing) {
            Box(
                modifier = Modifier
                    .size(DOT_SIZE)
                    .graphicsLayer {
                        val progress = pulse
                        scaleX = 1f + progress * 1.6f
                        scaleY = 1f + progress * 1.6f
                        alpha = (1f - progress) * 0.5f
                    }
                    .background(color, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(DOT_SIZE)
                .scale(if (pulsing) 0.85f else 1f)
                .background(color, CircleShape)
        )
    }
}

private val CONNECTED_COLOR = Color(0xFF4CAF50)
private val WORKING_COLOR = Color(0xFFFFA726)
private val DOT_SIZE = 8.dp
private val DOT_AREA = 18.dp
