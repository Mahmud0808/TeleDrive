package com.drdisagree.teledrive.core.telegram

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkSettlerTest {

    private val scope = TestScope()
    private var reconnects = 0
    private val settler = NetworkSettler<String>(scope, SETTLE_MS) { reconnects++ }

    @Test
    fun `the network present when watching starts is not a change`() {
        settler.available("wifi")
        settle()

        assertEquals(0, reconnects)
    }

    @Test
    fun `flipping away and back within the settle time does not reconnect`() {
        settler.available("wifi")
        settler.available("mobile")
        scope.advanceTimeBy(SETTLE_MS / 2)
        settler.available("wifi")
        settle()

        assertEquals(0, reconnects)
    }

    @Test
    fun `a brief drop of the same network does not reconnect`() {
        settler.available("wifi")
        settler.lost("wifi")
        scope.advanceTimeBy(SETTLE_MS / 2)
        settler.available("wifi")
        settle()

        assertEquals(0, reconnects)
    }

    @Test
    fun `a switch that holds reconnects once`() {
        settler.available("wifi")
        settler.lost("wifi")
        settler.available("wifi-2")
        settle()

        assertEquals(1, reconnects)
    }

    @Test
    fun `losing the network for good reconnects once`() {
        settler.available("wifi")
        settler.lost("wifi")
        settle()

        assertEquals(1, reconnects)
    }

    @Test
    fun `losing a network that is not the current one is ignored`() {
        settler.available("wifi")
        settler.lost("mobile")
        settle()

        assertEquals(0, reconnects)
    }

    private fun settle() {
        scope.advanceTimeBy(SETTLE_MS + 1)
        scope.runCurrent()
    }

    private companion object {
        const val SETTLE_MS = 10_000L
    }
}
