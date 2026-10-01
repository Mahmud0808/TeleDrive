package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.domain.model.BackupHold
import com.drdisagree.teledrive.domain.model.UserPreferences

/** Applies to backup transfers only; uploads and downloads the user starts are never held. */
object BackupGate {

    fun hold(prefs: UserPreferences, network: NetworkStatus, charging: Boolean): BackupHold? = when {
        prefs.backupChargingOnly && !charging -> BackupHold.CHARGER
        prefs.backupWifiOnly && network != NetworkStatus.UNMETERED -> BackupHold.WIFI
        else -> null
    }
}
