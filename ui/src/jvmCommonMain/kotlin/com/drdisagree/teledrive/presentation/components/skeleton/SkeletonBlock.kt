package com.drdisagree.teledrive.presentation.components.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape

@Composable
fun SkeletonBlock(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small
) {
    Box(modifier.clip(shape).background(brush))
}
