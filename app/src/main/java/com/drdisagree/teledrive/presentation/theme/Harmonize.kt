package com.drdisagree.teledrive.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.MaterialColors

/**
 * Pulls an authored color toward the active primary so it belongs to the
 * current palette while staying recognisably itself. Under dynamic color the
 * primary follows the wallpaper, so the whole chart re-tints with the system.
 */
@Composable
fun Color.harmonizedWithPrimary(): Color {
    val primary = MaterialTheme.colorScheme.primary
    return remember(this, primary) {
        Color(MaterialColors.harmonize(toArgb(), primary.toArgb()))
    }
}
