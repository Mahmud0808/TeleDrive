package com.drdisagree.teledrive.core.media

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object CaptureTime {

    private const val EARLIEST = 631_152_000_000L
    private const val FUTURE_SLACK_MS = 86_400_000L
    private val offsetPattern = Regex("""^([+-])(\d{2}):(\d{2})$""")

    fun fromExif(value: String?, offset: String?, zone: TimeZone = TimeZone.getDefault()): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsedZone = offset?.trim()?.let(::zoneOf) ?: zone
        return parse(text, "yyyy:MM:dd HH:mm:ss", parsedZone)
    }

    fun fromMediaDate(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val utc = TimeZone.getTimeZone("UTC")
        return parse(text, "yyyyMMdd'T'HHmmss.SSS'Z'", utc)
            ?: parse(text, "yyyyMMdd'T'HHmmss'Z'", utc)
            ?: parse(text, "yyyyMMdd'T'HHmmss", utc)
    }

    fun plausible(millis: Long?, now: Long = System.currentTimeMillis()): Long? =
        millis?.takeIf { it >= EARLIEST && it <= now + FUTURE_SLACK_MS }

    private fun parse(text: String, pattern: String, zone: TimeZone): Long? {
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = zone
            isLenient = false
        }
        val position = ParsePosition(0)
        val date = format.parse(text, position) ?: return null
        if (position.index != text.length) return null
        return plausible(date.time)
    }

    private fun zoneOf(offset: String): TimeZone? {
        val match = offsetPattern.matchEntire(offset) ?: return null
        val (sign, hours, minutes) = match.destructured
        return TimeZone.getTimeZone("GMT$sign$hours:$minutes")
    }
}
