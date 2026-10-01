package com.drdisagree.teledrive.core.transfer

/**
 * Averages each one-second interval and eases into the last reading, so speed and time left stop
 * jumping.
 */
class ProgressTicker(
    private val intervalMs: Long = INTERVAL_MS,
    private val smoothing: Double = SMOOTHING
) {

    private var lastBytes = 0L
    private var lastTick = 0L
    private var speed = 0L

    fun start(bytes: Long, now: Long) {
        lastBytes = bytes
        lastTick = now
        speed = 0
    }

    fun tick(bytes: Long, now: Long): Long? {
        val elapsed = now - lastTick
        if (elapsed < intervalMs) return null

        val moved = bytes - lastBytes
        val measured = if (moved > 0) moved * 1000 / elapsed else 0
        speed = if (speed <= 0) {
            measured
        } else {
            (measured * smoothing + speed * (1 - smoothing)).toLong()
        }
        lastBytes = bytes
        lastTick = now
        return speed
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
        const val SMOOTHING = 0.35
    }
}
