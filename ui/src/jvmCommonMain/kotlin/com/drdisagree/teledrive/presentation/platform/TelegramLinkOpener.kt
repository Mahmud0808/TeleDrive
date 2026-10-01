package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

interface TelegramLinkOpener {

    val canOpenTelegram: Boolean

    fun open(link: String): Boolean
}

val LocalTelegramLinkOpener = staticCompositionLocalOf<TelegramLinkOpener> {
    error("TelegramLinkOpener is not provided")
}
