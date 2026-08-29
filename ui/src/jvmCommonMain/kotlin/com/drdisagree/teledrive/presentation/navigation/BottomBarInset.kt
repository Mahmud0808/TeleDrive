package com.drdisagree.teledrive.presentation.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height the bottom navigation bar overlays on top of the current screen.
 * Screens add it to their scroll content padding instead of being resized, so
 * showing or hiding the bar never changes the viewport or the scroll position.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Height the bar would occupy in this layout, whether or not the current
 * screen sits under it. Layouts that navigate with a rail leave it at zero.
 * Screens resolve their own inset from this once, so a route change never
 * retimes the padding of a screen that is still on its way out.
 */
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

/**
 * Bar height above the system navigation inset: 12dp margin, the 64dp bar,
 * and another 12dp margin. Screens add this to their own bottom padding.
 */
val BottomBarHeight: Dp = 88.dp

/**
 * Extra lift for a floating action button. Scaffold already parks it above the
 * system inset with its own margin, so it needs less than the full bar height.
 */
val FabBottomBarInset: Dp = BottomBarHeight - 20.dp
