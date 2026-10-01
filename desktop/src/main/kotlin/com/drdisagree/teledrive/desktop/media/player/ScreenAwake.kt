package com.drdisagree.teledrive.desktop.media.player

import com.drdisagree.teledrive.core.common.SafeLog
import com.sun.jna.Platform

/**
 * Windows takes a thread execution state; macOS and Linux hold a child process while the inhibit
 * lasts. Best effort everywhere.
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

    private const val ES_SYSTEM_REQUIRED = 0x00000001
    private const val ES_DISPLAY_REQUIRED = 0x00000002
    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val TAG = "ScreenAwake"
}
