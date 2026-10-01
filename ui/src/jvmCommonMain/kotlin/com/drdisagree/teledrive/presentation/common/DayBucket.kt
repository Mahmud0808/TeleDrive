package com.drdisagree.teledrive.presentation.common

sealed interface DayBucket {
    data object Today : DayBucket
    data object Yesterday : DayBucket
    data class DaysAgo(val days: Int) : DayBucket
    data class Absolute(val text: String) : DayBucket
}
