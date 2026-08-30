package com.drdisagree.teledrive.presentation.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

private const val SCALE = 0.9f

private fun TextUnit.scaled(): TextUnit = if (isSpecified) this * SCALE else this

private fun TextStyle.scaled(): TextStyle = copy(
    fontSize = fontSize.scaled(),
    lineHeight = lineHeight.scaled()
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val TeleDriveTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.scaled(),
        displayLargeEmphasized = base.displayLargeEmphasized.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displayMediumEmphasized = base.displayMediumEmphasized.scaled(),
        displaySmall = base.displaySmall.scaled(),
        displaySmallEmphasized = base.displaySmallEmphasized.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleLargeEmphasized = base.titleLargeEmphasized.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleMediumEmphasized = base.titleMediumEmphasized.scaled(),
        titleSmall = base.titleSmall.scaled(),
        titleSmallEmphasized = base.titleSmallEmphasized.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.scaled(),
        bodySmall = base.bodySmall.scaled(),
        bodySmallEmphasized = base.bodySmallEmphasized.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelLargeEmphasized = base.labelLargeEmphasized.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelMediumEmphasized = base.labelMediumEmphasized.scaled(),
        labelSmall = base.labelSmall.scaled(),
        labelSmallEmphasized = base.labelSmallEmphasized.scaled()
    )
}
