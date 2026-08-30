package com.drdisagree.teledrive.desktop.transfer

import com.drdisagree.teledrive.core.transfer.MaintenanceScheduler

/**
 * Desktop has no background scheduler yet; maintenance runs only while the
 * app is open, driven by the transfer and publish schedulers it already has.
 */
class DesktopMaintenanceScheduler : MaintenanceScheduler {

    override fun scheduleUpdateCheck(enabled: Boolean) {
    }

    override fun scheduleAll(
        backupEnabled: Boolean,
        backupIntervalHours: Int,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        instantBackup: Boolean,
        updateChecks: Boolean
    ) {
    }
}
