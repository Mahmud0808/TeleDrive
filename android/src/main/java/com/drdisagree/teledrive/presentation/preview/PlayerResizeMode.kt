package com.drdisagree.teledrive.presentation.preview

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.ui.graphics.vector.ImageVector

enum class PlayerResizeMode(val icon: ImageVector, val description: String) {
    FIT(Icons.Filled.FitScreen, "Scaling: fit"),
    ZOOM(Icons.Filled.ZoomOutMap, "Scaling: crop to fill"),
    FILL(Icons.Filled.Fullscreen, "Scaling: stretch");

    fun next(): PlayerResizeMode = entries[(ordinal + 1) % entries.size]
}
