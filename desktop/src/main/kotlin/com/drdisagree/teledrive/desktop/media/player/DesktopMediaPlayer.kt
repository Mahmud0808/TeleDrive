package com.drdisagree.teledrive.desktop.media.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.desktop.ui.LocalFullscreenController
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.player_audio_track
import com.drdisagree.teledrive.resources.player_exit_full_screen
import com.drdisagree.teledrive.resources.player_full_screen
import com.drdisagree.teledrive.resources.player_hide_subtitles
import com.drdisagree.teledrive.resources.player_mute
import com.drdisagree.teledrive.resources.player_pause
import com.drdisagree.teledrive.resources.player_play
import com.drdisagree.teledrive.resources.player_repeat_off
import com.drdisagree.teledrive.resources.player_repeat_one
import com.drdisagree.teledrive.resources.player_replay
import com.drdisagree.teledrive.resources.player_scaling
import com.drdisagree.teledrive.resources.player_show_subtitles
import com.drdisagree.teledrive.resources.player_skip_back
import com.drdisagree.teledrive.resources.player_skip_forward
import com.drdisagree.teledrive.resources.player_unmute
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * Inline player backed by libVLC, mirroring the Android player: controls hide
 * while playback runs and return on mouse movement, seeking scrubs without
 * flooding the pipeline, and a wavy indicator marks buffering.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesktopMediaPlayer(
    mrl: String,
    isAudio: Boolean,
    modifier: Modifier = Modifier,
    onControlsVisibilityChange: (Boolean) -> Unit = {}
) {
    val factory = VlcPlayback.factory ?: return
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var playing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var timeMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(1.0f) }
    var repeatOne by remember { mutableStateOf(false) }
    var subtitlesOn by remember { mutableStateOf(false) }
    var scaling by remember { mutableStateOf(VideoScaling.FIT) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    val player = remember {
        factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
            if (!isAudio) videoSurface().set(composeVideoSurface { frame = it })
            events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    playing = true
                    finished = false
                    buffering = false
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    playing = false
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    playing = false
                    finished = true
                }

                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    buffering = newCache < 100f
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    timeMs = newTime
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    durationMs = newLength
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    playing = false
                    finished = true
                    buffering = false
                }
            })
        }
    }

    val fullscreenController = LocalFullscreenController.current
    DisposableEffect(mrl) {
        player.media().play(mrl)
        onDispose {
            runCatching {
                player.controls().stop()
                player.release()
            }
            fullscreenController?.let { controller ->
                if (controller.isFullscreen()) controller.toggle()
            }
        }
    }

    LaunchedEffect(playing, interactionTick) {
        if (!playing) {
            controlsVisible = true
            onControlsVisibilityChange(true)
            return@LaunchedEffect
        }
        controlsVisible = true
        onControlsVisibilityChange(true)
        delay(CONTROLS_HIDE_DELAY_MS)
        controlsVisible = false
        onControlsVisibilityChange(false)
    }

    val blankCursor = remember {
        PointerIcon(
            Toolkit.getDefaultToolkit().createCustomCursor(
                BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                Point(0, 0),
                "blank"
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerHoverIcon(if (controlsVisible) PointerIcon.Default else blankCursor)
            .pointerInput(Unit) {
                var lastPosition = Offset.Unspecified
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val position = event.changes.firstOrNull()?.position ?: continue
                        when (event.type) {
                            PointerEventType.Press -> interactionTick++
                            PointerEventType.Move -> {
                                if (lastPosition != Offset.Unspecified &&
                                    (position - lastPosition).getDistance() > MOVE_THRESHOLD_PX
                                ) {
                                    interactionTick++
                                }
                                lastPosition = position
                            }

                            else -> Unit
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isAudio) {
            Icon(
                imageVector = Icons.Filled.Audiotrack,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            frame?.let { current ->
                Image(
                    bitmap = current,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = scaling.contentScale
                )
            }
        }

        if (buffering && !finished) {
            CircularWavyProgressIndicator(modifier = Modifier.size(56.dp))
        }

        AnimatedVisibility(
            visible = controlsVisible || isAudio,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerControlsBar(
                player = player,
                isAudio = isAudio,
                playing = playing,
                finished = finished,
                timeMs = timeMs,
                durationMs = durationMs,
                muted = muted,
                rate = rate,
                repeatOne = repeatOne,
                subtitlesOn = subtitlesOn,
                scaling = scaling,
                onInteraction = { interactionTick++ },
                onPlayPause = {
                    when {
                        finished -> {
                            finished = false
                            player.controls().play()
                        }

                        playing -> player.controls().pause()
                        else -> player.controls().play()
                    }
                },
                onSeekTo = { millis -> player.controls().setTime(millis) },
                onToggleMute = {
                    muted = !muted
                    player.audio().isMute = muted
                },
                onRate = { value ->
                    rate = value
                    player.controls().setRate(value)
                },
                onToggleRepeat = {
                    repeatOne = !repeatOne
                    player.controls().repeat = repeatOne
                },
                onToggleSubtitles = {
                    subtitlesOn = !subtitlesOn
                    val tracks = player.subpictures().trackDescriptions()
                    val target = if (subtitlesOn) {
                        tracks.firstOrNull { it.id() >= 0 }?.id() ?: -1
                    } else {
                        -1
                    }
                    player.subpictures().setTrack(target)
                },
                onScaling = { scaling = it }
            )
        }
    }
}

internal enum class VideoScaling(val contentScale: ContentScale) {
    FIT(ContentScale.Fit),
    ZOOM(ContentScale.Crop),
    FILL(ContentScale.FillBounds);

    fun next(): VideoScaling = entries[(ordinal + 1) % entries.size]
}

@Composable
private fun PlayerControlsBar(
    player: MediaPlayer,
    isAudio: Boolean,
    playing: Boolean,
    finished: Boolean,
    timeMs: Long,
    durationMs: Long,
    muted: Boolean,
    rate: Float,
    repeatOne: Boolean,
    subtitlesOn: Boolean,
    scaling: VideoScaling,
    onInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onRate: (Float) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onScaling: (VideoScaling) -> Unit
) {
    var showRateMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableStateOf<Float?>(null) }
    val fullscreen = LocalFullscreenController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Slider(
            value = scrubTarget ?: if (durationMs > 0) timeMs.toFloat() else 0f,
            onValueChange = { value ->
                onInteraction()
                scrubTarget = value
            },
            onValueChangeFinished = {
                scrubTarget?.let { target -> onSeekTo(target.toLong()) }
                scrubTarget = null
            },
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                onInteraction()
                onSeekTo((timeMs - SKIP_MS).coerceAtLeast(0))
            }) {
                Icon(
                    Icons.Filled.Replay10,
                    contentDescription = stringResource(Res.string.player_skip_back),
                    tint = Color.White
                )
            }
            IconButton(onClick = {
                onInteraction()
                onPlayPause()
            }) {
                Icon(
                    imageVector = when {
                        finished -> Icons.Filled.Replay
                        playing -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = stringResource(
                        when {
                            finished -> Res.string.player_replay
                            playing -> Res.string.player_pause
                            else -> Res.string.player_play
                        }
                    ),
                    tint = Color.White
                )
            }
            IconButton(onClick = {
                onInteraction()
                onSeekTo((timeMs + SKIP_MS).coerceAtMost(durationMs))
            }) {
                Icon(
                    Icons.Filled.Forward10,
                    contentDescription = stringResource(Res.string.player_skip_forward),
                    tint = Color.White
                )
            }
            Text(
                text = Formatters.duration(scrubTarget?.toLong() ?: timeMs) +
                        " / " + Formatters.duration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Box(modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = {
                    onInteraction()
                    showRateMenu = true
                }) {
                    Text(text = "${rate}x", color = Color.White)
                }
                DropdownMenu(
                    expanded = showRateMenu,
                    onDismissRequest = { showRateMenu = false }
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { value ->
                        DropdownMenuItem(
                            text = { Text("${value}x") },
                            onClick = {
                                showRateMenu = false
                                onRate(value)
                            }
                        )
                    }
                }
            }
            IconButton(onClick = {
                onInteraction()
                onToggleRepeat()
            }) {
                Icon(
                    imageVector = if (repeatOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(
                        if (repeatOne) Res.string.player_repeat_one else Res.string.player_repeat_off
                    ),
                    tint = if (repeatOne) MaterialTheme.colorScheme.primary else Color.White
                )
            }
            if (!isAudio) {
                IconButton(onClick = {
                    onInteraction()
                    onToggleSubtitles()
                }) {
                    Icon(
                        imageVector = if (subtitlesOn) {
                            Icons.Filled.Subtitles
                        } else {
                            Icons.Filled.SubtitlesOff
                        },
                        contentDescription = stringResource(
                            if (subtitlesOn) {
                                Res.string.player_hide_subtitles
                            } else {
                                Res.string.player_show_subtitles
                            }
                        ),
                        tint = Color.White
                    )
                }
            }
            Box {
                IconButton(onClick = {
                    onInteraction()
                    showAudioMenu = true
                }) {
                    Icon(
                        Icons.Filled.Audiotrack,
                        contentDescription = stringResource(Res.string.player_audio_track),
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = showAudioMenu,
                    onDismissRequest = { showAudioMenu = false }
                ) {
                    player.audio().trackDescriptions().forEach { track ->
                        DropdownMenuItem(
                            text = { Text(track.description()) },
                            onClick = {
                                showAudioMenu = false
                                player.audio().setTrack(track.id())
                            }
                        )
                    }
                }
            }
            if (!isAudio) {
                IconButton(onClick = {
                    onInteraction()
                    onScaling(scaling.next())
                }) {
                    Icon(
                        imageVector = when (scaling) {
                            VideoScaling.FIT -> Icons.Filled.FitScreen
                            VideoScaling.ZOOM -> Icons.Filled.ZoomOutMap
                            VideoScaling.FILL -> Icons.Filled.Fullscreen
                        },
                        contentDescription = stringResource(Res.string.player_scaling),
                        tint = Color.White
                    )
                }
            }
            IconButton(onClick = {
                onInteraction()
                onToggleMute()
            }) {
                Icon(
                    imageVector = if (muted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = stringResource(
                        if (muted) Res.string.player_unmute else Res.string.player_mute
                    ),
                    tint = Color.White
                )
            }
            fullscreen?.let { controller ->
                IconButton(onClick = {
                    onInteraction()
                    controller.toggle()
                }) {
                    Icon(
                        imageVector = if (controller.isFullscreen()) {
                            Icons.Filled.FullscreenExit
                        } else {
                            Icons.Filled.Fullscreen
                        },
                        contentDescription = stringResource(
                            if (controller.isFullscreen()) {
                                Res.string.player_exit_full_screen
                            } else {
                                Res.string.player_full_screen
                            }
                        ),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private const val CONTROLS_HIDE_DELAY_MS = 3_000L
private const val MOVE_THRESHOLD_PX = 4f
private const val SKIP_MS = 10_000L
private val THUMB_WIDTH = 5.dp
private val THUMB_HEIGHT = 26.dp
