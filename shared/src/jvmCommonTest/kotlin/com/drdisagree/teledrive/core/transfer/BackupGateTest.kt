package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.domain.model.BackupHold
import com.drdisagree.teledrive.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupGateTest {

    @Test
    fun `charging only holds backups until the charger is plugged in`() {
        val prefs = UserPreferences(backupChargingOnly = true)

        assertEquals(BackupHold.CHARGER, BackupGate.hold(prefs, NetworkStatus.UNMETERED, charging = false))
        assertNull(BackupGate.hold(prefs, NetworkStatus.UNMETERED, charging = true))
    }

    @Test
    fun `wifi only holds backups on mobile data`() {
        val prefs = UserPreferences(backupWifiOnly = true)

        assertEquals(BackupHold.WIFI, BackupGate.hold(prefs, NetworkStatus.METERED, charging = true))
        assertNull(BackupGate.hold(prefs, NetworkStatus.UNMETERED, charging = true))
    }

    @Test
    fun `the charger is reported first when both conditions are unmet`() {
        val prefs = UserPreferences(backupChargingOnly = true, backupWifiOnly = true)

        assertEquals(BackupHold.CHARGER, BackupGate.hold(prefs, NetworkStatus.METERED, charging = false))
    }

    @Test
    fun `nothing is held when neither setting is on`() {
        assertNull(BackupGate.hold(UserPreferences(), NetworkStatus.METERED, charging = false))
    }
}
