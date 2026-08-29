package com.drdisagree.teledrive.desktop.media

import com.sun.jna.Platform
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Hands a stream URL to a real media player when one is installed, because
 * players buffer and seek far better than a browser tab. The browser is the
 * fallback every system still has.
 */
class ExternalMediaPlayer {

    fun play(url: String): Boolean {
        candidates().firstOrNull { File(it).exists() }?.let { player ->
            if (runCatching { ProcessBuilder(player, url).start() }.isSuccess) return true
        }
        return runCatching { Desktop.getDesktop().browse(URI(url)) }.isSuccess
    }

    private fun candidates(): List<String> = when {
        Platform.isWindows() -> {
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            listOf(
                "$programFiles\\VideoLAN\\VLC\\vlc.exe",
                "$programFilesX86\\VideoLAN\\VLC\\vlc.exe",
                "$programFiles\\mpv\\mpv.exe",
                "$programFiles\\MPC-HC\\mpc-hc64.exe",
                "$programFilesX86\\MPC-HC\\mpc-hc.exe",
                "$programFiles\\DAUM\\PotPlayer\\PotPlayerMini64.exe",
                "$programFilesX86\\DAUM\\PotPlayer\\PotPlayerMini.exe",
                "$programFilesX86\\Windows Media Player\\wmplayer.exe",
                "$programFiles\\Windows Media Player\\wmplayer.exe"
            )
        }

        Platform.isMac() -> listOf(
            "/Applications/VLC.app/Contents/MacOS/VLC",
            "/opt/homebrew/bin/mpv",
            "/usr/local/bin/mpv"
        )

        else -> listOf(
            "/usr/bin/vlc",
            "/usr/bin/mpv",
            "/snap/bin/vlc"
        )
    }
}
