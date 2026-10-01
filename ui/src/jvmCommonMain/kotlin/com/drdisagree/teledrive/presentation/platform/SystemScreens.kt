package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

interface SystemScreens {

    fun openAppSettings()

    fun openAllFilesAccess()
}

val LocalSystemScreens = staticCompositionLocalOf<SystemScreens> {
    error("SystemScreens is not provided")
}
