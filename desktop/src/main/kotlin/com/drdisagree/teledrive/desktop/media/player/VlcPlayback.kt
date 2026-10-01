package com.drdisagree.teledrive.desktop.media.player

import com.drdisagree.teledrive.core.common.SafeLog
import com.sun.jna.NativeLibrary
import java.io.File
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * Prefers the bundled libVLC (Windows, macOS), then the distro or system VLC; without either,
 * previews keep the external player.
 */
object VlcPlayback {

    val factory: MediaPlayerFactory? by lazy {
        runCatching { createFactory() }
            .onFailure { SafeLog.w(TAG, "Inline playback unavailable: ${it.message}") }
            .getOrNull()
    }

    val available: Boolean get() = factory != null

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

    private fun bundled(): VlcBundle? {
        val root = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?: return null
        if (File(root, "libvlc.dll").exists()) return VlcBundle(root, null)

        val libraries = File(root, "lib")
        if (!File(libraries, "libvlc.dylib").exists()) return null
        return VlcBundle(libraries, File(root, "plugins").takeIf { it.isDirectory })
    }

    private fun setPluginPath(plugins: File) {
        runCatching { PosixLibC.INSTANCE.setenv(PLUGIN_ENV_NAME, plugins.absolutePath, 1) }
            .onFailure { SafeLog.w(TAG, "Could not point libVLC at the bundled plugins") }
    }

    private val LIBVLC_ARGS = listOf("--no-video-title-show", "--quiet")
    private const val PLUGIN_ENV_NAME = "VLC_PLUGIN_PATH"
    private const val TAG = "VlcPlayback"
}
