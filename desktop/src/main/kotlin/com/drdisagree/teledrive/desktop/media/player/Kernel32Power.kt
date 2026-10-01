package com.drdisagree.teledrive.desktop.media.player

import com.sun.jna.Library
import com.sun.jna.Native

internal interface Kernel32Power : Library {
    fun SetThreadExecutionState(flags: Int): Int

    companion object {
        val INSTANCE: Kernel32Power by lazy {
            Native.load("kernel32", Kernel32Power::class.java)
        }
    }
}
