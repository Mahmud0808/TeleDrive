package com.drdisagree.teledrive.presentation.components

import androidx.compose.runtime.compositionLocalOf

/**
 * True when the user picked the compact layout density. Rows tighten their
 * padding and drop the secondary metadata line so more files fit on screen.
 */
val LocalCompactLayout = compositionLocalOf { false }
