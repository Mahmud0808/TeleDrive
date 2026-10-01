package com.drdisagree.teledrive.core.telegram

/**
 * Caps follow the upload part arithmetic (512 KiB parts, 4000 or 8000 of them), not the advertised
 * 2 or 4 GB: a file in between is refused by the server with FILE_PARTS_INVALID.
 */
data class TelegramLimits(
    val maxFileBytes: Long,
    val maxCaptionLength: Int
) {
    companion object {
        private const val MAX_PART_BYTES = 512L * 1024
        private const val MAX_PARTS = 4000L
        private const val MAX_PARTS_PREMIUM = 8000L

        val REGULAR = TelegramLimits(
            maxFileBytes = MAX_PARTS * MAX_PART_BYTES,
            maxCaptionLength = 1024
        )
        val PREMIUM = TelegramLimits(
            maxFileBytes = MAX_PARTS_PREMIUM * MAX_PART_BYTES,
            maxCaptionLength = 2048
        )

        fun forPremium(isPremium: Boolean): TelegramLimits = if (isPremium) PREMIUM else REGULAR
    }
}
