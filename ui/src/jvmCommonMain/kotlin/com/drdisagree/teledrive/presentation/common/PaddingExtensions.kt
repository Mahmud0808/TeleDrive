package com.drdisagree.teledrive.presentation.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * So content can draw edge to edge behind the system bars without its first or last items being cut
 * off.
 */
@Composable
fun PaddingValues.add(
    horizontal: Dp = 0.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + horizontal,
        end = calculateEndPadding(layoutDirection) + horizontal,
        top = calculateTopPadding() + top,
        bottom = calculateBottomPadding() + bottom
    )
}
