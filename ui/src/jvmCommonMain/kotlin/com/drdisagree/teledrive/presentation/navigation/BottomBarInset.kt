package com.drdisagree.teledrive.presentation.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Added to scroll padding instead of resizing the screen, so showing the bar never moves the
 * viewport.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Resolved once per screen, so a route change never retimes the padding of a screen on its way out.
 */
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

/** 12dp margin, the 64dp bar, and another 12dp margin. */
val BottomBarHeight: Dp = 88.dp

/** Scaffold already parks a FAB above the system inset with its own margin. */
val FabBottomBarInset: Dp = BottomBarHeight - 20.dp
