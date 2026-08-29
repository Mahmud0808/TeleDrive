package com.drdisagree.teledrive.desktop.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DesktopTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
