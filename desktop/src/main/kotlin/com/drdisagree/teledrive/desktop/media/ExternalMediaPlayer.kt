package com.drdisagree.teledrive.desktop.media

import com.sun.jna.Platform
import java.awt.Desktop
import java.io.File
import java.net.URI

/** Real players buffer and seek far better than a browser tab, which stays the fallback. */
class ExternalMediaPlayer {

    fun play(url: String): Boolean {
        candidates().firstOrNull { File(it).exists() }?.let { player ->
            if (runCatching { launch(player, url) }.isSuccess) return true
        }
        return runCatching { Desktop.getDesktop().browse(URI(url)) }.isSuccess
    }

    private fun launch(player: String, url: String) {
        val bundle = player.substringBefore(".app/", "")
            .takeIf { Platform.isMac() && it.isNotEmpty() }
        val command = if (bundle != null) {
            listOf("open", "-a", "$bundle.app", url)
        } else {
            listOf(player, url)
        }
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
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
