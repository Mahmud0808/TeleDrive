package com.drdisagree.teledrive.domain.usecase

import javax.inject.Inject

class TrashExpiryCalculator @Inject constructor() {

    /** [autoClearDays] <= 0 means never expire. */
    fun isExpired(trashedAt: Long, autoClearDays: Int, now: Long): Boolean {
        if (autoClearDays <= 0) return false
        val expiry = trashedAt + autoClearDays * MILLIS_PER_DAY
        return now >= expiry
    }

    fun expiryThreshold(autoClearDays: Int, now: Long): Long? {
        if (autoClearDays <= 0) return null
        return now - autoClearDays * MILLIS_PER_DAY
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
