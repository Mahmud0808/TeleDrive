package com.drdisagree.teledrive.core.telegram

/**
 * Account-dependent Telegram limits. Values follow the current Bot/MTProto
 * limits: 2 GiB per file for regular accounts, 4 GiB for Premium. Kept here so
 * a future Telegram change only touches this file.
 */
data class TelegramLimits(
    val maxFileBytes: Long,
    val maxCaptionLength: Int
) {
    companion object {
        private const val GIB = 1024L * 1024L * 1024L

        val REGULAR = TelegramLimits(maxFileBytes = 2 * GIB, maxCaptionLength = 1024)
        val PREMIUM = TelegramLimits(maxFileBytes = 4 * GIB, maxCaptionLength = 2048)

        fun forPremium(isPremium: Boolean): TelegramLimits = if (isPremium) PREMIUM else REGULAR
    }
}
