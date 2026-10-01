package com.drdisagree.teledrive.presentation.components

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * An action with [children] opens them in place of the current level instead of running [onClick].
 */
data class MenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val children: List<MenuAction> = emptyList(),
    val onClick: () -> Unit = {}
)
