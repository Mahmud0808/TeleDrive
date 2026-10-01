package com.drdisagree.teledrive.desktop.transfer

import com.drdisagree.teledrive.core.transfer.BackupResumeScheduler

class DesktopBackupResumeScheduler : BackupResumeScheduler {
    override fun resumeWhen(chargingOnly: Boolean, wifiOnly: Boolean) = Unit
}
