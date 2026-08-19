package com.drdisagree.teledrive.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashExpiryCalculatorTest {

    private val calculator = TrashExpiryCalculator()
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `zero days never expires`() {
        assertFalse(calculator.isExpired(trashedAt = 0, autoClearDays = 0, now = Long.MAX_VALUE))
        assertNull(calculator.expiryThreshold(0, now = 1000))
    }

    @Test
    fun `expires exactly after configured days`() {
        val trashedAt = 1_000_000L
        assertFalse(calculator.isExpired(trashedAt, 7, now = trashedAt + 7 * day - 1))
        assertTrue(calculator.isExpired(trashedAt, 7, now = trashedAt + 7 * day))
    }

    @Test
    fun `threshold matches cutoff`() {
        val now = 100 * day
        assertEquals(70 * day, calculator.expiryThreshold(30, now))
    }
}
