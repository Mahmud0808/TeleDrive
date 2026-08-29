package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Screens whose implementation differs per platform, slotted into the nav host. */
interface PlatformScreens {

    @Composable
    fun Preview(onBack: () -> Unit, onEditNote: (String, String) -> Unit)
}

val LocalPlatformScreens = staticCompositionLocalOf<PlatformScreens> {
    error("PlatformScreens is not provided")
}
