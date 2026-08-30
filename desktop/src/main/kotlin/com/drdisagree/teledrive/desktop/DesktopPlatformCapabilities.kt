package com.drdisagree.teledrive.desktop

import com.drdisagree.teledrive.presentation.platform.PlatformCapabilities

/** No background scheduler or media watcher yet, and no runtime permissions. */
class DesktopPlatformCapabilities : PlatformCapabilities {

    override val supportsAutoBackup: Boolean = false

    override val requiresPermissions: Boolean = false

    override val supportsPullToRefresh: Boolean = false

    override val supportsAppLock: Boolean = false

    override val supportsScreenCaptureBlocking: Boolean = false

    override val supportsDynamicColor: Boolean = false
}
