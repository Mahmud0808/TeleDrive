package com.drdisagree.teledrive.desktop.media.player

import com.drdisagree.teledrive.core.common.SafeLog
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import java.io.File
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * Loads libVLC once for the whole app, preferring the copy bundled with the
 * install so playback works without VLC on the machine. Windows and macOS ship
 * that copy, Linux takes libVLC from the distro, and a system VLC is the
 * fallback everywhere. When neither exists the preview quietly keeps its
 * external player and download buttons.
 */
object VlcPlayback {

    val factory: MediaPlayerFactory? by lazy {
        runCatching { createFactory() }
            .onFailure { SafeLog.w(TAG, "Inline playback unavailable: ${it.message}") }
            .getOrNull()
    }

    val available: Boolean get() = factory != null

    private class Bundle(val libraries: File, val plugins: File?)

    private fun createFactory(): MediaPlayerFactory {
        val bundle = bundled()
        return if (bundle != null) {
            bundle.plugins?.let { setPluginPath(it) }
            NativeLibrary.addSearchPath("libvlc", bundle.libraries.absolutePath)
            NativeLibrary.addSearchPath("libvlccore", bundle.libraries.absolutePath)
            MediaPlayerFactory(null as NativeDiscovery?, LIBVLC_ARGS)
        } else {
            MediaPlayerFactory(NativeDiscovery(), LIBVLC_ARGS)
        }
    }

    private fun bundled(): Bundle? {
        val root = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?: return null
        if (File(root, "libvlc.dll").exists()) return Bundle(root, null)

        val libraries = File(root, "lib")
        if (!File(libraries, "libvlc.dylib").exists()) return null
        return Bundle(libraries, File(root, "plugins").takeIf { it.isDirectory })
    }

    private fun setPluginPath(plugins: File) {
        runCatching { PosixLibC.INSTANCE.setenv(PLUGIN_ENV_NAME, plugins.absolutePath, 1) }
            .onFailure { SafeLog.w(TAG, "Could not point libVLC at the bundled plugins") }
    }

    private interface PosixLibC : Library {
        fun setenv(name: String, value: String, overwrite: Int): Int

        companion object {
            val INSTANCE: PosixLibC by lazy { Native.load("c", PosixLibC::class.java) }
        }
    }

    private val LIBVLC_ARGS = listOf("--no-video-title-show", "--quiet")
    private const val PLUGIN_ENV_NAME = "VLC_PLUGIN_PATH"
    private const val TAG = "VlcPlayback"
}
