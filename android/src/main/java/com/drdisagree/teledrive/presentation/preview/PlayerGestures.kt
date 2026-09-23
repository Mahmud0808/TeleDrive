package com.drdisagree.teledrive.presentation.preview

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.player_brightness
import com.drdisagree.teledrive.resources.player_volume
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.stringResource

private enum class Adjustment { BRIGHTNESS, VOLUME }

private data class AdjustmentLevel(val kind: Adjustment, val level: Float)

/**
 * Touch surface under the controls. A tap toggles the chrome, a vertical drag
 * on the left half sets screen brightness and one on the right half sets media
 * volume, matching what every other video player on the platform does. The
 * readout and the transport buttons share the middle of the screen, so a drag
 * dismisses the chrome and the chrome coming back dismisses the readout. The
 * brightness override lives on the window, so it reverts to the system value
 * when the player leaves the composition.
 */
@Composable
fun PlayerGestureArea(
    onTap: () -> Unit,
    onAdjustStart: () -> Unit,
    dragEnabled: Boolean,
    chromeVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val tap by rememberUpdatedState(onTap)
    val adjustStart by rememberUpdatedState(onAdjustStart)

    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember(audioManager) {
        (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1).coerceAtLeast(1)
    }

    var brightness by remember(activity) { mutableFloatStateOf(systemBrightness(activity)) }
    var volume by remember(audioManager) {
        mutableFloatStateOf(
            currentVolume(
                audioManager,
                maxVolume
            )
        )
    }
    var adjustment by remember { mutableStateOf<AdjustmentLevel?>(null) }

    DisposableEffect(activity) {
        onDispose { applyBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }

    LaunchedEffect(chromeVisible) {
        if (chromeVisible) adjustment = null
    }

    LaunchedEffect(adjustment) {
        if (adjustment == null) return@LaunchedEffect
        delay(HUD_TIMEOUT_MS.milliseconds)
        adjustment = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap() }
                }
                .pointerInput(dragEnabled, maxVolume) {
                    if (!dragEnabled) return@pointerInput
                    var onLeftHalf = false
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            onLeftHalf = offset.x < size.width / 2f
                            volume = currentVolume(audioManager, maxVolume)
                            adjustStart()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val travel = (size.height * DRAG_RANGE).coerceAtLeast(1f)
                            val step = -dragAmount / travel
                            if (onLeftHalf) {
                                brightness = (brightness + step).coerceIn(MIN_BRIGHTNESS, 1f)
                                applyBrightness(activity, brightness)
                                adjustment = AdjustmentLevel(Adjustment.BRIGHTNESS, brightness)
                            } else {
                                volume = (volume + step).coerceIn(0f, 1f)
                                audioManager?.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (volume * maxVolume).roundToInt(),
                                    0
                                )
                                adjustment = AdjustmentLevel(Adjustment.VOLUME, volume)
                            }
                        }
                    )
                }
        )
        AnimatedVisibility(
            visible = adjustment != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            adjustment?.let { AdjustmentHud(it) }
        }
    }
}

@Composable
private fun AdjustmentHud(adjustment: AdjustmentLevel) {
    val brightnessLabel = stringResource(Res.string.player_brightness)
    val volumeLabel = stringResource(Res.string.player_volume)
    val label = when (adjustment.kind) {
        Adjustment.BRIGHTNESS -> brightnessLabel
        Adjustment.VOLUME -> volumeLabel
    }
    val icon = when (adjustment.kind) {
        Adjustment.BRIGHTNESS -> when {
            adjustment.level <= MIN_BRIGHTNESS -> Icons.Filled.BrightnessLow
            adjustment.level <= HALF -> Icons.Filled.BrightnessMedium
            else -> Icons.Filled.BrightnessHigh
        }

        Adjustment.VOLUME -> when {
            adjustment.level <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
            adjustment.level <= HALF -> Icons.AutoMirrored.Filled.VolumeDown
            else -> Icons.AutoMirrored.Filled.VolumeUp
        }
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = HUD_ALPHA),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
            LinearProgressIndicator(
                progress = { adjustment.level },
                modifier = Modifier.width(HUD_BAR_WIDTH)
            )
            Text(
                text = "${(adjustment.level * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private fun currentVolume(audioManager: AudioManager?, maxVolume: Int): Float {
    val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    return (current.toFloat() / maxVolume).coerceIn(0f, 1f)
}

private fun systemBrightness(activity: Activity?): Float {
    val override = activity?.window?.attributes?.screenBrightness ?: -1f
    if (override >= 0f) return override
    val resolver = activity?.contentResolver ?: return DEFAULT_BRIGHTNESS
    val value = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull() ?: return DEFAULT_BRIGHTNESS
    return (value / MAX_SYSTEM_BRIGHTNESS).coerceIn(0f, 1f)
}

private fun applyBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    window.attributes = window.attributes.apply { screenBrightness = value }
}

private const val DRAG_RANGE = 0.7f
private const val MIN_BRIGHTNESS = 0.01f
private const val DEFAULT_BRIGHTNESS = 0.5f
private const val MAX_SYSTEM_BRIGHTNESS = 255f
private const val HALF = 0.5f
private const val HUD_ALPHA = 0.85f
private const val HUD_TIMEOUT_MS = 900L
private val HUD_BAR_WIDTH = 120.dp
