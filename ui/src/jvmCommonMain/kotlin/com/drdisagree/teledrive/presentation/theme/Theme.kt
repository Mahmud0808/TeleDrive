package com.drdisagree.teledrive.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TeleDriveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = platformColorScheme(darkTheme, dynamicColor)
        ?: if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme

    PlatformThemeSideEffects(darkTheme)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = TeleDriveTypography,
        shapes = TeleDriveShapes,
        content = content
    )
}
