package com.drdisagree.teledrive.desktop.ui

import androidx.compose.runtime.Composable
import com.drdisagree.teledrive.presentation.platform.PlatformScreens

object DesktopPlatformScreens : PlatformScreens {

    @Composable
    override fun Preview(onBack: () -> Unit, onEditNote: (String, String) -> Unit) {
        DesktopPreviewScreen(onBack = onBack)
    }
}
