package com.drdisagree.teledrive.core.transfer

/** Schedules the app's recurring maintenance work on the platform scheduler. */
interface MaintenanceScheduler {

    fun scheduleUpdateCheck(enabled: Boolean)

    fun scheduleAll(
        backupEnabled: Boolean,
        backupIntervalHours: Int,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        instantBackup: Boolean = false,
        updateChecks: Boolean = true
    )
}
