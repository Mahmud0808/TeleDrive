package com.drdisagree.teledrive.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.drdisagree.teledrive.R

enum class TopLevelDestination(
    val route: Route,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME(Route.Home, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    FILES(
        Route.Files(),
        R.string.nav_files,
        Icons.AutoMirrored.Outlined.InsertDriveFile,
        Icons.AutoMirrored.Filled.InsertDriveFile
    ),
    GALLERY(Route.Gallery, R.string.nav_gallery, Icons.Outlined.Photo, Icons.Filled.Photo),
    SETTINGS(Route.Settings, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}
