package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** What the running platform can actually do, so the UI never offers more. */
interface PlatformCapabilities {

    /** True where a background scheduler and media watcher exist. */
    val supportsAutoBackup: Boolean

    /** True where file access needs runtime permissions. */
    val requiresPermissions: Boolean

    /** True on touch platforms where the pull gesture does not fight scrolling. */
    val supportsPullToRefresh: Boolean
}

val LocalPlatformCapabilities = staticCompositionLocalOf<PlatformCapabilities> {
    error("PlatformCapabilities is not provided")
}
