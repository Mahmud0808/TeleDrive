package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Hands a tg: login link to a Telegram app installed on this same device, so
 * confirming does not require scanning across devices.
 */
interface TelegramLinkOpener {

    val canOpenTelegram: Boolean

    fun open(link: String): Boolean
}

val LocalTelegramLinkOpener = staticCompositionLocalOf<TelegramLinkOpener> {
    error("TelegramLinkOpener is not provided")
}
