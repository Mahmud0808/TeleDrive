package com.drdisagree.teledrive.core.telegram

/**
 * Account-dependent Telegram limits.
 *
 * The size cap is derived from how an upload is actually carried: a part is at
 * most 512 KiB and there are at most 4000 of them, 8000 with Premium. That puts
 * the real ceiling at 2000 MiB and 4000 MiB, a little under the "2 GB" and
 * "4 GB" the apps advertise. A file in that gap looks acceptable to the client
 * and is then refused by the server with FILE_PARTS_INVALID, so the numbers
 * here follow the part arithmetic instead of being rounded to whole gibibytes.
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
