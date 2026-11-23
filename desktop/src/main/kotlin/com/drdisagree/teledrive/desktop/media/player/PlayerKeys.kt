package com.drdisagree.teledrive.desktop.media.player

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent

class PlayerKeys {
    var handler: ((KeyEvent) -> Boolean)? = null
}

val LocalPlayerKeys = staticCompositionLocalOf<PlayerKeys?> { null }
