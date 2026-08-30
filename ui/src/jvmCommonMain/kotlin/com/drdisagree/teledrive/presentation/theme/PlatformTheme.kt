package com.drdisagree.teledrive.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal expect fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme?

@Composable
internal expect fun PlatformThemeSideEffects(darkTheme: Boolean)
