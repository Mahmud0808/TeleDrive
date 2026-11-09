package com.drdisagree.teledrive.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.drdisagree.teledrive.core.telegram.TelegramConnectionState
import com.drdisagree.teledrive.presentation.components.ConnectionIndicator
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.home_status_syncing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ConnectionStatusTest {

    @get:Rule
    val rule = createComposeRule()

    private var offline by mutableStateOf(false)
    private var connection by mutableStateOf(TelegramConnectionState.CONNECTING)
    private var status: ConnectionStatus? = null
    private var firstConnectClaimed = false

    private fun start(alreadyAnnounced: Boolean = false) {
        firstConnectClaimed = alreadyAnnounced
        rule.mainClock.autoAdvance = false
        rule.setContent {
            status = rememberConnectionStatus(offline, connection, known = true) {
                (!firstConnectClaimed).also { firstConnectClaimed = true }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun advance(ms: Long) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    @Test
    fun `connecting shows, then the first connect after launch shows connected briefly`() {
        start()
        advance(100)
        assertEquals(ConnectionIndicator.WORKING, status?.indicator)

        connection = TelegramConnectionState.READY
        advance(100)
        assertEquals(ConnectionIndicator.CONNECTED, status?.indicator)
        advance(2_500)
        assertNull(status)
    }

    @Test
    fun `reopening home after the first connect shows nothing`() {
        connection = TelegramConnectionState.READY
        start(alreadyAnnounced = true)
        advance(3_000)
        assertNull(status)
    }

    @Test
    fun `a lasting outage shows, then a short recovery note`() {
        offline = true
        start(alreadyAnnounced = true)
        advance(2_500)
        assertEquals(ConnectionIndicator.OFFLINE, status?.indicator)

        offline = false
        connection = TelegramConnectionState.READY
        advance(100)
        assertEquals(ConnectionIndicator.CONNECTED, status?.indicator)
        advance(2_500)
        assertNull(status)
    }

    @Test
    fun `catching up shows syncing until ready`() {
        connection = TelegramConnectionState.UPDATING
        start(alreadyAnnounced = true)
        advance(5_000)
        assertEquals(Res.string.home_status_syncing, status?.labelRes)

        connection = TelegramConnectionState.READY
        advance(100)
        assertEquals(ConnectionIndicator.CONNECTED, status?.indicator)
        advance(2_500)
        assertNull(status)
    }
}
