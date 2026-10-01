package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalAppIcon = staticCompositionLocalOf<@Composable (Modifier) -> Unit> {
    error("App icon is not provided")
}
