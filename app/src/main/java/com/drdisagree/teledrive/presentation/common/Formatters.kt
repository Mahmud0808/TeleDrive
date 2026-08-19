package com.drdisagree.teledrive.presentation.common

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {

    fun bytes(value: Long): String {
        if (value <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val exponent = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
        val scaled = value / 1024.0.pow(exponent)
        return if (exponent == 0) {
            "$value B"
        } else {
            String.format(Locale.getDefault(), "%.1f %s", scaled, units[exponent])
        }
    }

    fun badgeCount(count: Int): String =
        if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString()

    fun speed(bytesPerSecond: Long): String = "${bytes(bytesPerSecond)}/s"

    fun date(epochMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

    fun dayStart(epochMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dayBucket(dayStartMillis: Long, now: Long = System.currentTimeMillis()): DayBucket {
        val days = ((dayStart(now) - dayStartMillis) / DAY_MILLIS).toInt()
        return when {
            days <= 0 -> DayBucket.Today
            days == 1 -> DayBucket.Yesterday
            days <= 7 -> DayBucket.DaysAgo(days)
            else -> DayBucket.Absolute(
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(dayStartMillis))
            )
        }
    }

    fun dateTime(epochMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(epochMillis))

    fun duration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    fun eta(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    private const val MAX_BADGE_COUNT = 99
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** Coarse age buckets, in the units a person would actually say. */
    fun relativeAge(epochMillis: Long, now: Long = System.currentTimeMillis()): AgeBucket {
        val elapsed = (now - epochMillis).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            minutes < 1L -> AgeBucket.JustNow
            minutes < 60L -> AgeBucket.Minutes(minutes.toInt())
            hours < 24L -> AgeBucket.Hours(hours.toInt())
            days < 30L -> AgeBucket.Days(days.toInt())
            else -> AgeBucket.Longer(epochMillis)
        }
    }
}

/** Result of [Formatters.relativeAge], resolved to text by the caller. */
sealed interface AgeBucket {
    data object JustNow : AgeBucket
    data class Minutes(val value: Int) : AgeBucket
    data class Hours(val value: Int) : AgeBucket
    data class Days(val value: Int) : AgeBucket
    data class Longer(val epochMillis: Long) : AgeBucket
}

/** Result of [Formatters.dayBucket], resolved to text by the caller. */
sealed interface DayBucket {
    data object Today : DayBucket
    data object Yesterday : DayBucket
    data class DaysAgo(val days: Int) : DayBucket
    data class Absolute(val text: String) : DayBucket
}
