package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

fun interface UrlOpener {

    fun open(url: String)
}

val LocalUrlOpener = staticCompositionLocalOf<UrlOpener> {
    error("UrlOpener is not provided")
}
