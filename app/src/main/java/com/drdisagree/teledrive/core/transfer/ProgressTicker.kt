package com.drdisagree.teledrive.core.transfer

/**
 * Paces progress writes to one a second and reports the speed as an average
 * over that second rather than over whatever moment the last update landed in.
 *
 * Telegram reports bytes far more often than a person can read them, and a
 * reading taken over a fraction of a second swings with every burst, which
 * makes the speed and the time left jump about. Averaging the interval, then
 * easing the result into the previous one, gives a figure that settles.
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

    /** The speed to record, or null when the next tick is not due yet. */
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
