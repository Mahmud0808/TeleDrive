package com.drdisagree.teledrive.core.transfer

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
