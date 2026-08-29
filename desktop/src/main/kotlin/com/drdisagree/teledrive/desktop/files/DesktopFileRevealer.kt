package com.drdisagree.teledrive.desktop.files

import com.drdisagree.teledrive.presentation.platform.FileRevealer
import com.sun.jna.Platform
import java.awt.Desktop
import java.io.File

/**
 * Selects the file in the platform's file manager. Explorer takes the file
 * through /select; elsewhere the JDK API or the parent folder stands in.
 */
class DesktopFileRevealer : FileRevealer {

    override fun reveal(path: String): Boolean {
        val target = File(path)
        if (!target.exists()) return false
        if (Platform.isWindows()) {
            return runCatching {
                ProcessBuilder("explorer.exe", "/select,", target.absolutePath).start()
            }.isSuccess
        }
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            return runCatching { desktop.browseFileDirectory(target) }.isSuccess
        }
        return runCatching { desktop.open(target.parentFile) }.isSuccess
    }
}
