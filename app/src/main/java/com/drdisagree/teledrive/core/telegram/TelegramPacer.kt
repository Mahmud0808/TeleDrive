package com.drdisagree.teledrive.core.telegram

import com.drdisagree.teledrive.core.common.SafeLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Paces every call that creates or edits a message. Telegram answers too many
 * of those with a flood wait, and a flood wait applies to the account rather
 * than to one request, so the wait is held here and every caller respects it
 * instead of the other workers carrying on regardless.
 */
@Singleton
class TelegramPacer @Inject constructor() {

    private val mutex = Mutex()
    private var tokens = BURST.toDouble()
    private var lastRefillAt = System.currentTimeMillis()

    @Volatile
    private var floodUntil = 0L

    /** Seconds left of an account-wide flood wait, or zero when clear. */
    val floodWaitSeconds: Int
        get() = ((floodUntil - System.currentTimeMillis()) / 1000L)
            .coerceAtLeast(0L)
            .toInt()

    suspend fun <T> paced(block: suspend () -> T): T {
        awaitClearance()
        return try {
            block()
        } catch (e: TelegramException) {
            if (e.isRateLimit) {
                recordFloodWait(e.retryAfterSeconds ?: FALLBACK_SECONDS)
            }
            throw e
        }
    }

    /** Holds every caller until the flood wait passes and a token is free. */
    private suspend fun awaitClearance() {
        while (true) {
            val remaining = floodUntil - System.currentTimeMillis()
            if (remaining > 0) {
                delay(remaining.milliseconds)
                continue
            }
            val wait = mutex.withLock { takeToken() }
            if (wait <= 0) return
            delay(wait.milliseconds)
        }
    }

    /** Returns how long to wait for the next token, or zero when one was taken. */
    private fun takeToken(): Long {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastRefillAt).coerceAtLeast(0)
        lastRefillAt = now
        tokens = (tokens + elapsed * TOKENS_PER_MS).coerceAtMost(BURST.toDouble())
        if (tokens >= 1.0) {
            tokens -= 1.0
            return 0
        }
        return ((1.0 - tokens) / TOKENS_PER_MS).toLong().coerceAtLeast(MIN_WAIT_MS)
    }

    private fun recordFloodWait(seconds: Int) {
        val until = System.currentTimeMillis() + (seconds + 1) * 1000L
        if (until > floodUntil) {
            floodUntil = until
            SafeLog.w(TAG, "Flood wait for ${seconds}s, holding every request")
        }
    }

    private companion object {
        const val TAG = "TelegramPacer"
        const val PER_MINUTE = 20
        const val BURST = 5
        const val TOKENS_PER_MS = PER_MINUTE / 60_000.0
        const val MIN_WAIT_MS = 50L
        const val FALLBACK_SECONDS = 5
    }
}
