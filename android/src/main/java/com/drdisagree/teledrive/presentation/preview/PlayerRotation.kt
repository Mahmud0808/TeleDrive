package com.drdisagree.teledrive.presentation.preview

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class PlayerRotation(
    val icon: ImageVector,
    val description: String,
    val orientation: Int
) {
    AUTO(
        Icons.Filled.ScreenRotation,
        "Rotation: follow device",
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ),
    LANDSCAPE(
        Icons.Filled.ScreenLockLandscape,
        "Rotation: locked landscape",
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ),
    PORTRAIT(
        Icons.Filled.ScreenLockPortrait,
        "Rotation: locked portrait",
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    );

    fun next(): PlayerRotation = entries[(ordinal + 1) % entries.size]
}
