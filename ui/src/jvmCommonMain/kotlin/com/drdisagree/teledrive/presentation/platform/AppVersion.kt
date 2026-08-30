package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppVersion = staticCompositionLocalOf<String> {
    error("App version is not provided")
}
