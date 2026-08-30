package com.drdisagree.teledrive.desktop.media.player

import com.drdisagree.teledrive.core.common.SafeLog
import com.sun.jna.NativeLibrary
import java.io.File
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * Loads libVLC once for the whole app, preferring the copy bundled with the
 * install so playback works without VLC on the machine. A system VLC is the
 * fallback, and when neither exists the preview quietly keeps its external
 * player and download buttons.
 */
object VlcPlayback {

    val factory: MediaPlayerFactory? by lazy {
        runCatching { createFactory() }
            .onFailure { SafeLog.w(TAG, "Inline playback unavailable: ${it.message}") }
            .getOrNull()
    }

    val available: Boolean get() = factory != null

    private fun createFactory(): MediaPlayerFactory {
        val bundled = bundledDirectory()
        return if (bundled != null) {
            NativeLibrary.addSearchPath("libvlc", bundled.absolutePath)
            NativeLibrary.addSearchPath("libvlccore", bundled.absolutePath)
            MediaPlayerFactory(null as NativeDiscovery?, LIBVLC_ARGS)
        } else {
            MediaPlayerFactory(NativeDiscovery(), LIBVLC_ARGS)
        }
    }

    private fun bundledDirectory(): File? =
        System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?.takeIf { File(it, "libvlc.dll").exists() }

    private val LIBVLC_ARGS = listOf("--no-video-title-show", "--quiet")
    private const val TAG = "VlcPlayback"
}
