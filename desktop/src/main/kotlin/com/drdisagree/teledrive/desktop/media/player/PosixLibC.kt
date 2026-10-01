package com.drdisagree.teledrive.desktop.media.player

import com.sun.jna.Library
import com.sun.jna.Native

internal interface PosixLibC : Library {
    fun setenv(name: String, value: String, overwrite: Int): Int

    companion object {
        val INSTANCE: PosixLibC by lazy { Native.load("c", PosixLibC::class.java) }
    }
}
