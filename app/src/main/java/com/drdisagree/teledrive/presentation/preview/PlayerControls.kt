package com.drdisagree.teledrive.presentation.preview

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.player_fewer_controls
import com.drdisagree.teledrive.resources.player_hide_subtitles
import com.drdisagree.teledrive.resources.player_more_controls
import com.drdisagree.teledrive.resources.player_mute
import com.drdisagree.teledrive.resources.player_pause
import com.drdisagree.teledrive.resources.player_play
import com.drdisagree.teledrive.resources.player_repeat_off
import com.drdisagree.teledrive.resources.player_repeat_one
import com.drdisagree.teledrive.resources.player_replay
import com.drdisagree.teledrive.resources.player_show_subtitles
import com.drdisagree.teledrive.resources.player_unmute
import com.drdisagree.teledrive.resources.preview_back_10_seconds
import com.drdisagree.teledrive.resources.preview_forward_10_seconds
import com.drdisagree.teledrive.resources.preview_next_audio_track
import com.drdisagree.teledrive.resources.preview_playback_speed
import com.drdisagree.teledrive.presentation.common.Formatters
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Playback controls drawn in Compose over the video surface. The Media3
 * controller is not used: its layout ignores window insets, leaves no side
 * padding, and anchors its settings popup to the top of the view.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerControls(
    player: Player,
    visible: Boolean,
    audioOnly: Boolean,
    resizeMode: PlayerResizeMode,
    onCycleResizeMode: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var ended by remember(player) { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var playWhenReady by remember(player) { mutableStateOf(player.playWhenReady) }
    var duration by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0)) }
    var scrubTarget by remember(player) { mutableStateOf<Float?>(null) }
    var pendingSeek by remember(player) { mutableStateOf<Long?>(null) }
    var speed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    var muted by remember(player) { mutableStateOf(player.volume == 0f) }
    var repeatOne by remember(player) {
        mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE)
    }
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var subtitlesOn by remember(player) { mutableStateOf(false) }
    var rotation by remember { mutableStateOf(PlayerRotation.AUTO) }
    var expanded by remember { mutableStateOf(false) }
    var showSpeeds by remember { mutableStateOf(false) }

    val activity = LocalActivity.current

    /**
     * Streaming from Telegram means a seek, or the first frames of a file, can
     * take a moment. Showing play there reads as "nothing happened", so the
     * button reports that the player is waiting on data instead.
     */
    val waitingForData = playbackState == Player.STATE_BUFFERING ||
            (playWhenReady && !playing && !ended && playbackState != Player.STATE_IDLE)

    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                ended = state == Player.STATE_ENDED
                duration = player.duration.coerceAtLeast(0)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    position = newPosition.positionMs.coerceAtLeast(0)
                    pendingSeek = null
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReadyNow: Boolean, reason: Int) {
                playWhenReady = playWhenReadyNow
            }

            override fun onTracksChanged(current: Tracks) {
                tracks = current
                subtitlesOn = current.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(player, visible, playing) {
        while (visible) {
            val current = player.currentPosition.coerceAtLeast(0)
            val target = pendingSeek
            if (target == null || abs(current - target) <= SEEK_TOLERANCE_MS) {
                pendingSeek = null
                position = current
            }
            duration = player.duration.coerceAtLeast(0)
            delay(PROGRESS_INTERVAL_MS.milliseconds)
        }
    }

    LaunchedEffect(visible) {
        if (!visible) expanded = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = TOP_BAR_HEIGHT, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.Top
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Ltr
                        ) {
                            ControlButton(
                                icon = if (muted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                description = if (muted) stringResource(Res.string.player_unmute) else stringResource(
                                    Res.string.player_mute
                                ),
                                active = muted,
                                onClick = {
                                    onInteraction()
                                    muted = !muted
                                    player.volume = if (muted) 0f else 1f
                                }
                            )
                            ControlButton(
                                icon = rotation.icon,
                                description = rotation.description,
                                active = rotation != PlayerRotation.AUTO,
                                onClick = {
                                    onInteraction()
                                    rotation = rotation.next()
                                    activity?.requestedOrientation = rotation.orientation
                                }
                            )
                            if (!audioOnly) {
                                ControlButton(
                                    icon = resizeMode.icon,
                                    description = resizeMode.description,
                                    onClick = {
                                        onInteraction()
                                        onCycleResizeMode()
                                    }
                                )
                            }
                            ExtraControl(expanded) {
                                ControlButton(
                                    icon = Icons.Filled.SlowMotionVideo,
                                    description = stringResource(Res.string.preview_playback_speed),
                                    active = speed != 1f,
                                    onClick = {
                                        onInteraction()
                                        showSpeeds = true
                                    }
                                )
                            }
                            ExtraControl(expanded) {
                                ControlButton(
                                    icon = if (repeatOne) {
                                        Icons.Filled.RepeatOne
                                    } else {
                                        Icons.Filled.Repeat
                                    },
                                    description = if (repeatOne) stringResource(Res.string.player_repeat_off) else stringResource(
                                        Res.string.player_repeat_one
                                    ),
                                    active = repeatOne,
                                    onClick = {
                                        onInteraction()
                                        repeatOne = !repeatOne
                                        player.repeatMode = if (repeatOne) {
                                            Player.REPEAT_MODE_ONE
                                        } else {
                                            Player.REPEAT_MODE_OFF
                                        }
                                    }
                                )
                            }
                            if (!audioOnly) {
                                ExtraControl(expanded) {
                                    ControlButton(
                                        icon = if (subtitlesOn) {
                                            Icons.Filled.Subtitles
                                        } else {
                                            Icons.Filled.SubtitlesOff
                                        },
                                        description = if (subtitlesOn) {
                                            stringResource(Res.string.player_hide_subtitles)
                                        } else {
                                            stringResource(Res.string.player_show_subtitles)
                                        },
                                        active = subtitlesOn,
                                        enabled = textGroups.isNotEmpty(),
                                        onClick = {
                                            onInteraction()
                                            subtitlesOn = !subtitlesOn
                                            player.trackSelectionParameters =
                                                player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(
                                                        C.TRACK_TYPE_TEXT,
                                                        !subtitlesOn
                                                    )
                                                    .build()
                                        }
                                    )
                                }
                            }
                            ExtraControl(expanded) {
                                ControlButton(
                                    icon = Icons.Filled.Audiotrack,
                                    description = stringResource(Res.string.preview_next_audio_track),
                                    enabled = audioGroups.size > 1,
                                    onClick = {
                                        onInteraction()
                                        val selected = audioGroups.indexOfFirst { it.isSelected }
                                        val next = audioGroups[(selected + 1) % audioGroups.size]
                                        player.trackSelectionParameters =
                                            player.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(
                                                    TrackSelectionOverride(next.mediaTrackGroup, 0)
                                                )
                                                .build()
                                    }
                                )
                            }
                        }
                    }
                }
                ControlButton(
                    icon = if (expanded) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
                    description = if (expanded) stringResource(Res.string.player_fewer_controls) else stringResource(
                        Res.string.player_more_controls
                    ),
                    onClick = {
                        onInteraction()
                        expanded = !expanded
                    }
                )
            }

            if (!audioOnly) {
                TransportRow(
                    player = player,
                    playing = playing,
                    ended = ended,
                    waiting = waitingForData,
                    onInteraction = onInteraction,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (audioOnly) {
                    TransportRow(
                        player = player,
                        playing = playing,
                        ended = ended,
                        waiting = waitingForData,
                        onInteraction = onInteraction,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                    )
                }
                Slider(
                    value = scrubTarget ?: position.toFloat(),
                    onValueChange = { value ->
                        onInteraction()
                        scrubTarget = value
                    },
                    onValueChangeFinished = {
                        scrubTarget?.let { target ->
                            val millis = target.toLong()
                            position = millis
                            pendingSeek = millis
                            player.seekTo(millis)
                        }
                        scrubTarget = null
                    },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Formatters.duration(scrubTarget?.toLong() ?: position),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (speed != 1f) {
                        Text(
                            text = speedLabel(speed),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        text = Formatters.duration(duration),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSpeeds) {
        ModalBottomSheet(onDismissRequest = { showSpeeds = false }) {
            Text(
                text = stringResource(Res.string.preview_playback_speed),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            SPEEDS.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = speed == option,
                            role = Role.RadioButton,
                            onClick = {
                                speed = option
                                player.setPlaybackSpeed(option)
                                onInteraction()
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = speed == option, onClick = null)
                    Text(
                        text = speedLabel(option),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(8.dp)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportRow(
    player: Player,
    playing: Boolean,
    ended: Boolean,
    waiting: Boolean,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                onInteraction()
                player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
            },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
                    .copy(alpha = TRANSPORT_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                Icons.Filled.Replay10,
                contentDescription = stringResource(Res.string.preview_back_10_seconds)
            )
        }
        FilledIconButton(
            onClick = {
                onInteraction()
                when {
                    ended -> {
                        player.seekTo(0)
                        player.play()
                    }

                    player.isPlaying -> player.pause()
                    else -> player.play()
                }
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
                    .copy(alpha = TRANSPORT_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.size(PLAY_BUTTON_SIZE)
        ) {
            if (waiting) {
                LoadingIndicator(
                    color = LocalContentColor.current,
                    modifier = Modifier.size(PLAY_ICON_SIZE)
                )
            } else {
                Icon(
                    imageVector = when {
                        ended -> Icons.Filled.Replay
                        playing -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = when {
                        ended -> stringResource(Res.string.player_replay)
                        playing -> stringResource(Res.string.player_pause)
                        else -> stringResource(Res.string.player_play)
                    },
                    modifier = Modifier.size(PLAY_ICON_SIZE)
                )
            }
        }
        IconButton(
            onClick = {
                onInteraction()
                val limit = player.duration.coerceAtLeast(0)
                player.seekTo((player.currentPosition + SEEK_STEP_MS).coerceAtMost(limit))
            },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
                    .copy(alpha = TRANSPORT_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                Icons.Filled.Forward10,
                contentDescription = stringResource(Res.string.preview_forward_10_seconds)
            )
        }
    }
}

@Composable
private fun ExtraControl(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
    ) {
        content()
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = SURFACE_ALPHA)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA)
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

/** Video scaling applied to the surface, cycled from the controls. */
enum class PlayerResizeMode(val icon: ImageVector, val description: String) {
    FIT(Icons.Filled.FitScreen, "Scaling: fit"),
    ZOOM(Icons.Filled.ZoomOutMap, "Scaling: crop to fill"),
    FILL(Icons.Filled.Fullscreen, "Scaling: stretch");

    fun next(): PlayerResizeMode = entries[(ordinal + 1) % entries.size]
}

/** Screen orientation while a video is open, cycled from the controls. */
private enum class PlayerRotation(
    val icon: ImageVector,
    val description: String,
    val orientation: Int
) {
    AUTO(
        Icons.Filled.ScreenRotation,
        "Rotation: follow device",
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ),
    LANDSCAPE(
        Icons.Filled.ScreenLockLandscape,
        "Rotation: locked landscape",
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ),
    PORTRAIT(
        Icons.Filled.ScreenLockPortrait,
        "Rotation: locked portrait",
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    );

    fun next(): PlayerRotation = entries[(ordinal + 1) % entries.size]
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val SEEK_STEP_MS = 10_000L
private const val PROGRESS_INTERVAL_MS = 250L
private const val SEEK_TOLERANCE_MS = 500L
private const val SCRIM_ALPHA = 0.35f
private const val SURFACE_ALPHA = 0.35f
private const val TRANSPORT_ALPHA = 0.7f
private val THUMB_WIDTH = 5.dp
private val THUMB_HEIGHT = 26.dp
private val PLAY_BUTTON_SIZE = 72.dp
private val PLAY_ICON_SIZE = 36.dp
private val TOP_BAR_HEIGHT = 64.dp
