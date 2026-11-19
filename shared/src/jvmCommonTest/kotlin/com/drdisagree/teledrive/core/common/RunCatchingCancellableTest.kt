package com.drdisagree.teledrive.core.common

import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCatchingCancellableTest {

    @Test
    fun `successful blocks return their value`() {
        assertEquals(42, runCatchingCancellable { 42 }.getOrThrow())
    }

    @Test
    fun `ordinary failures are still returned`() {
        val result = runCatchingCancellable { error("boom") }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `cancellation is rethrown so timeouts can stop`() {
        assertThrows(CancellationException::class.java) {
            runCatchingCancellable { throw CancellationException("stop") }
        }
    }
}
