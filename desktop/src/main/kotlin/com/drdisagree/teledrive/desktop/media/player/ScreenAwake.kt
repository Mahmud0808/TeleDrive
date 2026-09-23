package com.drdisagree.teledrive.desktop.media.player

import com.drdisagree.teledrive.core.common.SafeLog
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform

/**
 * Holds off the screen blanker while a video plays, the desktop counterpart to
 * the keep-screen-on flag on Android. Windows takes a thread execution state,
 * macOS and Linux hold a child process for as long as the inhibit should last,
 * because both expose the request through a command rather than a plain C call.
 * Every platform is best effort: playback still works when the request fails.
 */
object ScreenAwake {

    private var inhibitor: Process? = null
    private var active = false

    init {
        Runtime.getRuntime().addShutdownHook(Thread { inhibitor?.destroyForcibly() })
    }

    @Synchronized
    fun keepAwake(enabled: Boolean) {
        if (enabled == active) return
        active = enabled
        runCatching { if (enabled) acquire() else release() }
            .onFailure { SafeLog.w(TAG, "Could not hold the screen awake: ${it.message}") }
    }

    private fun acquire() {
        if (Platform.isWindows()) {
            Kernel32Power.INSTANCE.SetThreadExecutionState(
                ES_CONTINUOUS or ES_DISPLAY_REQUIRED or ES_SYSTEM_REQUIRED
            )
            return
        }
        val command = if (Platform.isMac()) {
            listOf("caffeinate", "-d")
        } else {
            listOf(
                "systemd-inhibit",
                "--what=idle",
                "--who=TeleDrive",
                "--why=Video playback",
                "--mode=block",
                "sleep",
                "infinity"
            )
        }
        inhibitor = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    private fun release() {
        if (Platform.isWindows()) {
            Kernel32Power.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS)
        }
        inhibitor?.destroy()
        inhibitor = null
    }

    private interface Kernel32Power : Library {
        fun SetThreadExecutionState(flags: Int): Int

        companion object {
            val INSTANCE: Kernel32Power by lazy {
                Native.load("kernel32", Kernel32Power::class.java)
            }
        }
    }

    private const val ES_SYSTEM_REQUIRED = 0x00000001
    private const val ES_DISPLAY_REQUIRED = 0x00000002
    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val TAG = "ScreenAwake"
}
