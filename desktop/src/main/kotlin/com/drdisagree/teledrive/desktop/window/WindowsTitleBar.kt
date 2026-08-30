package com.drdisagree.teledrive.desktop.window

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * Windows leaves the title bar light no matter what the app draws, so the
 * dark preference is pushed down to the compositor. Attribute 20 is
 * DWMWA_USE_IMMERSIVE_DARK_MODE; builds before 20H1 used 19.
 */
object WindowsTitleBar {

    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val USE_IMMERSIVE_DARK_MODE_LEGACY = 19

    fun setDark(window: Window, dark: Boolean) {
        if (!Platform.isWindows()) return
        val pointer = Native.getComponentPointer(window) ?: return
        val hwnd = WinDef.HWND(pointer)
        val value = IntByReference(if (dark) 1 else 0)
        val result = DwmApi.INSTANCE.DwmSetWindowAttribute(
            hwnd,
            USE_IMMERSIVE_DARK_MODE,
            value,
            Int.SIZE_BYTES
        )
        if (result != 0) {
            DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                USE_IMMERSIVE_DARK_MODE_LEGACY,
                value,
                Int.SIZE_BYTES
            )
        }
        window.repaint()
    }
}
