package com.drdisagree.teledrive.core.transfer

internal class RecordingBackupResumeScheduler : BackupResumeScheduler {
    val requests = mutableListOf<Pair<Boolean, Boolean>>()

    override fun resumeWhen(chargingOnly: Boolean, wifiOnly: Boolean) {
        requests += chargingOnly to wifiOnly
    }
}
