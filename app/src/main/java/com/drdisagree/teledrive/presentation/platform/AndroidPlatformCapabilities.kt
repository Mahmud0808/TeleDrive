package com.drdisagree.teledrive.presentation.platform

import android.os.Build

class AndroidPlatformCapabilities : PlatformCapabilities {

    override val supportsAutoBackup: Boolean = true

    override val requiresPermissions: Boolean = true

    override val supportsPullToRefresh: Boolean = true

    override val supportsAppLock: Boolean = true

    override val supportsScreenCaptureBlocking: Boolean = true

    override val supportsDynamicColor: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
