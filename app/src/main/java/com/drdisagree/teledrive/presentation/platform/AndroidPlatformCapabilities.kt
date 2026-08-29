package com.drdisagree.teledrive.presentation.platform

class AndroidPlatformCapabilities : PlatformCapabilities {

    override val supportsAutoBackup: Boolean = true

    override val requiresPermissions: Boolean = true
}
