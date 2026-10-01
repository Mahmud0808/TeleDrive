package com.drdisagree.teledrive.desktop

import com.drdisagree.teledrive.presentation.platform.PlatformCapabilities

class DesktopPlatformCapabilities : PlatformCapabilities {

    override val supportsAutoBackup: Boolean = false

    override val requiresPermissions: Boolean = false

    override val supportsPullToRefresh: Boolean = false

    override val supportsAppLock: Boolean = false

    override val supportsScreenCaptureBlocking: Boolean = false

    override val supportsDynamicColor: Boolean = false
}
