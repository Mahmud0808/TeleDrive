package com.drdisagree.teledrive.core.telegram

/**
 * Wraps a TDLib error. [retryAfterSeconds] is set for FLOOD_WAIT (429) errors.
 */
class TelegramException(
    val code: Int,
    override val message: String,
    val retryAfterSeconds: Int? = null
) : Exception(message) {

    val isRateLimit: Boolean get() = code == 429

    val isNetworkFailure: Boolean get() = code == 500 && message.contains("network", ignoreCase = true)

    companion object {
        private val floodWaitRegex = Regex("retry after (\\d+)", RegexOption.IGNORE_CASE)

        fun from(code: Int, message: String): TelegramException {
            val retryAfter = if (code == 429) {
                floodWaitRegex.find(message)?.groupValues?.get(1)?.toIntOrNull()
            } else null
            return TelegramException(code, message, retryAfter)
        }
    }
}
