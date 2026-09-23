package com.drdisagree.teledrive.presentation.preview

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.drdisagree.teledrive.core.media.TelegramDataSourceFactory
import com.drdisagree.teledrive.core.media.ThumbnailModel
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.player_codec_unsupported
import com.drdisagree.teledrive.resources.player_playback_failed
import com.drdisagree.teledrive.resources.player_unlock_controls
import java.io.File
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * Media3 player for local and Telegram-streamed playback. PlayerView renders
 * the video only; the controls are Compose, so they follow the app theme and
 * the window insets. Controls share visibility with the surrounding chrome and
 * time out together with it while playback runs.
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaPlayer(
    content: PreviewContent,
    title: String,
    fileId: String,
    dataSourceFactory: TelegramDataSourceFactory,
    isActivePage: Boolean,
    controlsVisible: Boolean = true,
    allowBackgroundPlayback: Boolean = false,
    preferredAudioLanguage: String = "",
    preferredSubtitleLanguage: String = "",
    onPreferredAudioLanguage: (String) -> Unit = {},
    onPreferredSubtitleLanguage: (String) -> Unit = {},
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePage by rememberUpdatedState(isActivePage)
    val notifyControls by rememberUpdatedState(onControlsVisibilityChanged)
    val audioLanguage by rememberUpdatedState(preferredAudioLanguage)
    val subtitleLanguage by rememberUpdatedState(preferredSubtitleLanguage)

    val holder = remember(content) { mutableStateOf<ExoPlayer?>(null) }
    val player = holder.value

    LaunchedEffect(holder, isActivePage) {
        if (!isActivePage || holder.value != null) return@LaunchedEffect
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        holder.value = ExoPlayer.Builder(context, renderers).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage(audioLanguage.ifEmpty { null })
                .setPreferredTextLanguage(subtitleLanguage.ifEmpty { null })
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleLanguage.isEmpty())
                .build()
            when (content) {
                is PreviewContent.LocalMedia -> {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(File(content.path))))
                }

                is PreviewContent.StreamedMedia -> {
                    val factory = if (content.parts.isNotEmpty()) {
                        dataSourceFactory.createParted(
                            parts = content.parts,
                            encrypted = content.encrypted
                        )
                    } else {
                        dataSourceFactory.create(content.remoteFileId)
                    }
                    val source = ProgressiveMediaSource.Factory(factory)
                        .createMediaSource(MediaItem.fromUri("telegram://stream"))
                    setMediaSource(source)
                }

                else -> Unit
            }
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(holder) {
        onDispose { holder.value?.release() }
    }

    DisposableEffect(player, isActivePage) {
        if (!isActivePage) player?.pause()
        onDispose { }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val backgroundAllowed by rememberUpdatedState(allowBackgroundPlayback)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !backgroundAllowed) {
                player?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val audioOnly = when (content) {
        is PreviewContent.LocalMedia -> content.isAudio
        is PreviewContent.StreamedMedia -> content.isAudio
        else -> false
    }

    var interactionTick by remember { mutableIntStateOf(0) }
    var locked by remember(content) { mutableStateOf(false) }
    var lockHintVisible by remember(content) { mutableStateOf(false) }
    val visibleControls by rememberUpdatedState(controlsVisible)
    var resizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    var playing by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var sized by remember(player) { mutableStateOf((player?.videoSize?.width ?: 0) > 0) }
    var decoderFailed by remember(player) { mutableStateOf(false) }
    var playbackFailed by remember(player) { mutableStateOf(false) }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                sized = videoSize.width > 0 && videoSize.height > 0
            }

            override fun onPlayerError(error: PlaybackException) {
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                        decoderFailed = true

                    else -> playbackFailed = true
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(controlsVisible, playing, interactionTick, activePage, audioOnly) {
        if (!controlsVisible || !playing || !activePage || audioOnly) return@LaunchedEffect
        delay(CONTROLLER_TIMEOUT_MS.milliseconds)
        notifyControls(false)
    }

    LaunchedEffect(locked, lockHintVisible) {
        if (!locked) {
            lockHintVisible = false
            return@LaunchedEffect
        }
        if (!lockHintVisible) return@LaunchedEffect
        delay(CONTROLLER_TIMEOUT_MS.milliseconds)
        lockHintVisible = false
    }

    val activity = LocalActivity.current
    DisposableEffect(activity, playing, activePage, audioOnly) {
        val window = activity?.window
        if (playing && activePage && !audioOnly) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < LIGHT_LUMINANCE
    val stageColor = when {
        audioOnly -> ComposeColor.Transparent
        darkTheme -> ComposeColor.Black
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Box(modifier = modifier.fillMaxSize().background(stageColor)) {
        if (audioOnly && player != null) {
            AudioStage(
                player = player,
                fallbackTitle = title,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (!audioOnly && player != null) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        this.player = player
                        setBackgroundColor(Color.TRANSPARENT)
                        setShutterBackgroundColor(Color.TRANSPARENT)
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = when (resizeMode) {
                        PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        PlayerResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (!audioOnly && !sized) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(stageColor)
            ) {
                AsyncImage(
                    model = ThumbnailModel(fileId),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (decoderFailed || playbackFailed) {
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(
                        if (decoderFailed) {
                            Res.string.player_codec_unsupported
                        } else {
                            Res.string.player_playback_failed
                        }
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        PlayerGestureArea(
            onTap = {
                if (locked) {
                    lockHintVisible = !lockHintVisible
                } else {
                    notifyControls(!visibleControls)
                }
            },
            onAdjustStart = { if (visibleControls) notifyControls(false) },
            dragEnabled = !locked && !audioOnly,
            chromeVisible = controlsVisible,
            modifier = Modifier.matchParentSize()
        )
        if (player != null && !locked) {
            PlayerControls(
                player = player,
                visible = controlsVisible,
                audioOnly = audioOnly,
                resizeMode = resizeMode,
                onCycleResizeMode = { resizeMode = resizeMode.next() },
                onInteraction = { interactionTick++ },
                onLock = {
                    locked = true
                    lockHintVisible = true
                    notifyControls(false)
                },
                onPreferredAudioLanguage = onPreferredAudioLanguage,
                onPreferredSubtitleLanguage = onPreferredSubtitleLanguage
            )
        }
        if (locked) {
            AnimatedVisibility(
                visible = lockHintVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .navigationBarsPadding()
                    .padding(start = 24.dp)
            ) {
                FilledIconButton(
                    onClick = {
                        locked = false
                        lockHintVisible = false
                        notifyControls(true)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ComposeColor.Black.copy(alpha = LOCK_ALPHA),
                        contentColor = ComposeColor.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = stringResource(Res.string.player_unlock_controls)
                    )
                }
            }
        }
    }
}

/**
 * Stage for audio files, which have no video surface to look at. Cover art
 * embedded in the file is used when the container carries it, with the track
 * tags falling back to the file name.
 */
@Composable
private fun AudioStage(player: Player, fallbackTitle: String, modifier: Modifier = Modifier) {
    var metadata by remember(player) { mutableStateOf(player.mediaMetadata) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                metadata = mediaMetadata
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val artwork = remember(metadata) {
        metadata.artworkData?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            modifier = Modifier.size(ARTWORK_SIZE)
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AUDIO_ICON_SIZE)
                    )
                }
            }
        }
        Text(
            text = metadata.title?.toString() ?: fallbackTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 24.dp)
        )
        metadata.artist?.toString()?.let { artist ->
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        metadata.albumTitle?.toString()?.let { album ->
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private const val CONTROLLER_TIMEOUT_MS = 3_000L
private const val LOCK_ALPHA = 0.7f
private const val LIGHT_LUMINANCE = 0.5f

private val AUDIO_ICON_SIZE = 96.dp

private val ARTWORK_SIZE = 260.dp