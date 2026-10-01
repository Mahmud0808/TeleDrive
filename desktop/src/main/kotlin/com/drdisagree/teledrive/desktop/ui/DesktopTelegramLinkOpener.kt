package com.drdisagree.teledrive.desktop.ui

import com.drdisagree.teledrive.presentation.platform.TelegramLinkOpener
import com.sun.jna.Platform
import java.awt.Desktop
import java.net.URI

/**
 * Windows registers tg: in the registry and Linux as an x-scheme-handler, so the probe launches
 * nothing.
 */
class DesktopTelegramLinkOpener : TelegramLinkOpener {

    override val canOpenTelegram: Boolean by lazy {
        if (Platform.isWindows()) {
            runCatching {
                ProcessBuilder("reg", "query", "HKEY_CLASSES_ROOT\\tg")
                    .redirectErrorStream(true)
                    .start()
                    .apply { inputStream.readBytes() }
                    .waitFor() == 0
            }.getOrDefault(false)
        } else {
            runCatching {
                val handler = ProcessBuilder("xdg-mime", "query", "default", "x-scheme-handler/tg")
                    .redirectErrorStream(true)
                    .start()
                    .apply { waitFor() }
                    .inputStream.bufferedReader().use { it.readText() }.trim()
                handler.isNotEmpty() && handler != "No application registered"
            }.getOrDefault(false)
        }
    }

    override fun open(link: String): Boolean = runCatching {
        Desktop.getDesktop().browse(URI(link))
        true
    }.getOrDefault(false)
}
