package com.drdisagree.teledrive.presentation.preview

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.drdisagree.teledrive.core.media.TelegramDataSourceFactory
import com.drdisagree.teledrive.core.media.ThumbnailModel
import kotlinx.coroutines.delay
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
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePage by rememberUpdatedState(isActivePage)
    val notifyControls by rememberUpdatedState(onControlsVisibilityChanged)

    val holder = remember(content) { mutableStateOf<ExoPlayer?>(null) }
    val player = holder.value

    LaunchedEffect(holder, isActivePage) {
        if (!isActivePage || holder.value != null) return@LaunchedEffect
        holder.value = ExoPlayer.Builder(context).build().apply {
            when (content) {
                is PreviewContent.LocalMedia -> {
                    setMediaItem(MediaItem.fromUri("file://${content.path}"))
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
    val visibleControls by rememberUpdatedState(controlsVisible)
    var resizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    var playing by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var sized by remember(player) { mutableStateOf((player?.videoSize?.width ?: 0) > 0) }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                sized = videoSize.width > 0 && videoSize.height > 0
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

    Box(modifier = modifier.fillMaxSize()) {
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
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                AsyncImage(
                    model = ThumbnailModel(fileId),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { notifyControls(!visibleControls) }
                }
        )
        if (player != null) {
            PlayerControls(
                player = player,
                visible = controlsVisible,
                audioOnly = audioOnly,
                resizeMode = resizeMode,
                onCycleResizeMode = { resizeMode = resizeMode.next() },
                onInteraction = { interactionTick++ }
            )
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

private val AUDIO_ICON_SIZE = 96.dp

private val ARTWORK_SIZE = 260.dp