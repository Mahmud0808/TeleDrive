package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Opens system settings pages the app cannot render itself. */
interface SystemScreens {

    fun openAppSettings()

    fun openAllFilesAccess()
}

val LocalSystemScreens = staticCompositionLocalOf<SystemScreens> {
    error("SystemScreens is not provided")
}
