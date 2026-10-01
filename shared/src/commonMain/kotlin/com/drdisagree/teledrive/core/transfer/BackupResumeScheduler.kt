package com.drdisagree.teledrive.core.transfer

interface BackupResumeScheduler {
    fun resumeWhen(chargingOnly: Boolean, wifiOnly: Boolean)
}
