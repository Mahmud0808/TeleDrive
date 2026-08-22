package com.drdisagree.teledrive.presentation.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.components.ErrorState
import com.drdisagree.teledrive.presentation.components.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF viewer backed by the platform PdfRenderer. Pages render lazily and are
 * kept as bitmaps only while visible. Rendering is serialized because
 * PdfRenderer is not thread-safe. Pinch zooms the whole document; a two finger
 * drag, or a one finger drag while zoomed, pans it.
 */
@Composable
fun PdfPreview(path: String, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scale = remember(path) { Animatable(1f) }
    val offset = remember(path) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var viewport by remember(path) { mutableStateOf(IntSize.Zero) }
    val rendererState = remember(path) { mutableStateOf<PdfRenderer?>(null) }
    val renderMutex = remember(path) { Mutex() }
    val error = remember(path) { mutableStateOf<String?>(null) }

    val openFailedMessage = stringResource(R.string.preview_pdf_open_failed)
    DisposableEffect(path) {
        try {
            val descriptor = ParcelFileDescriptor.open(
                File(path),
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            rendererState.value = PdfRenderer(descriptor)
        } catch (_: Exception) {
            error.value = openFailedMessage
        }
        onDispose {
            runCatching { rendererState.value?.close() }
            rendererState.value = null
        }
    }

    error.value?.let {
        ErrorState(message = it, modifier = modifier)
        return
    }
    val renderer = rendererState.value ?: run {
        LoadingState(modifier = modifier)
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(path) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val zoomedIn = scale.value > 1f
                        val target = if (zoomedIn) 1f else DOUBLE_TAP_SCALE
                        val focus = if (zoomedIn) {
                            Offset.Zero
                        } else {
                            clampPdfOffset(
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
            .pointerInput(path) {
                detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                    val target = (scale.value * zoom).coerceIn(1f, MAX_SCALE)
                    val moved = if (target > 1f) {
                        clampPdfOffset(offset.value + pan, target, viewport)
                    } else {
                        Offset.Zero
                    }
                    scope.launch {
                        launch { scale.snapTo(target) }
                        launch { offset.snapTo(moved) }
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                },
            contentPadding = WindowInsets.systemBars
                .asPaddingValues()
                .add(top = PreviewTopBarHeight + 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = renderer.pageCount, key = { it }) { pageIndex ->
                PdfPage(
                    renderer = renderer,
                    renderMutex = renderMutex,
                    pageIndex = pageIndex,
                    onJumpToPage = { target ->
                        scope.launch { listState.animateScrollToItem(target) }
                    }
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    renderMutex: Mutex,
    pageIndex: Int,
    onJumpToPage: (Int) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val rendered by produceState<RenderedPage?>(initialValue = null, renderer, pageIndex) {
        value = withContext(Dispatchers.IO) {
            renderMutex.withLock {
                runCatching {
                    renderer.openPage(pageIndex).use { page ->
                        val scale = TARGET_WIDTH.toFloat() / page.width
                        val bitmap = createBitmap(
                            TARGET_WIDTH,
                            (page.height * scale).toInt().coerceAtLeast(1)
                        )
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        RenderedPage(bitmap, linksOf(page))
                    }
                }.getOrNull()
            }
        }
    }

    val page = rendered
    if (page == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PLACEHOLDER_RATIO)
        )
        return
    }

    val ratio = page.bitmap.width.toFloat() / page.bitmap.height
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
    ) {
        Image(
            bitmap = page.bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.preview_page_number, pageIndex + 1),
            modifier = Modifier.fillMaxSize()
        )
        page.links.forEach { link ->
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * link.left, y = maxHeight * link.top)
                    .size(
                        width = maxWidth * (link.right - link.left),
                        height = maxHeight * (link.bottom - link.top)
                    )
                    .clickable {
                        link.uri?.let { uriHandler.openUri(it) }
                            ?: link.targetPage?.let(onJumpToPage)
                    }
            )
        }
    }
}

/**
 * Link rectangles are only exposed by the platform from Android 15 on. Older
 * releases render the page without them rather than shipping a PDF parser.
 */
private fun linksOf(page: PdfRenderer.Page): List<PdfLink> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return emptyList()
    val width = page.width.toFloat()
    val height = page.height.toFloat()
    if (width <= 0f || height <= 0f) return emptyList()

    return runCatching {
        val external = page.linkContents.flatMap { content ->
            content.bounds.map { bounds -> bounds to content.uri.toString() }
        }.map { (bounds, uri) -> bounds.toLink(width, height, uri = uri) }

        val internal = page.gotoLinks.flatMap { content ->
            content.bounds.map { bounds -> bounds to content.destination.pageNumber }
        }.map { (bounds, target) -> bounds.toLink(width, height, targetPage = target) }

        external + internal
    }.getOrDefault(emptyList())
}

private fun RectF.toLink(
    pageWidth: Float,
    pageHeight: Float,
    uri: String? = null,
    targetPage: Int? = null
): PdfLink = PdfLink(
    left = (left / pageWidth).coerceIn(0f, 1f),
    top = (top / pageHeight).coerceIn(0f, 1f),
    right = (right / pageWidth).coerceIn(0f, 1f),
    bottom = (bottom / pageHeight).coerceIn(0f, 1f),
    uri = uri,
    targetPage = targetPage
)

private data class RenderedPage(val bitmap: Bitmap, val links: List<PdfLink>)

/** Link bounds as fractions of the page, so they survive zoom and resize. */
private data class PdfLink(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val uri: String?,
    val targetPage: Int?
)

private const val TARGET_WIDTH = 1080
private const val PLACEHOLDER_RATIO = 0.7f

/** Keeps the zoomed page covering the viewport, so no empty edge shows. */
private fun clampPdfOffset(target: Offset, scale: Float, viewport: IntSize): Offset {
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
