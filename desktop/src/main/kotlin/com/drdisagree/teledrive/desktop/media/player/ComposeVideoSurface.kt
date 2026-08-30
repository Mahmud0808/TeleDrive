package com.drdisagree.teledrive.desktop.media.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.nio.ByteBuffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat

/**
 * Receives decoded frames from libVLC and turns each into an [ImageBitmap]
 * the composable draws. Frames arrive as BGRA which matches Skia's layout,
 * so a frame is one buffer copy, no per-pixel conversion.
 */
internal fun composeVideoSurface(onFrame: (ImageBitmap) -> Unit): CallbackVideoSurface {
    var width = 0
    var height = 0
    var pixels = ByteArray(0)

    val bufferFormat = object : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            width = sourceWidth
            height = sourceHeight
            pixels = ByteArray(sourceWidth * sourceHeight * 4)
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }
    }

    val render = object : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) = Unit

        override fun unlock(mediaPlayer: MediaPlayer) = Unit

        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<ByteBuffer>,
            bufferFormat: BufferFormat,
            displayWidth: Int,
            displayHeight: Int
        ) {
            if (width == 0 || height == 0) return
            val source = nativeBuffers[0]
            source.rewind()
            source.get(pixels, 0, minOf(pixels.size, source.remaining()))
            val bitmap = Bitmap()
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            bitmap.allocPixels(info)
            bitmap.installPixels(info, pixels, width * 4)
            onFrame(bitmap.asComposeImageBitmap())
        }
    }

    return CallbackVideoSurface(
        bufferFormat,
        render,
        true,
        VideoSurfaceAdapters.getVideoSurfaceAdapter()
    )
}
