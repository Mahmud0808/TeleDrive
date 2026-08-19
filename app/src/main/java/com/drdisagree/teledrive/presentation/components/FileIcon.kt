package com.drdisagree.teledrive.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.drdisagree.teledrive.domain.model.FileCategory

fun iconFor(category: FileCategory): ImageVector = when (category) {
    FileCategory.IMAGE -> Icons.Outlined.Image
    FileCategory.VIDEO -> Icons.Outlined.VideoFile
    FileCategory.AUDIO -> Icons.Outlined.AudioFile
    FileCategory.DOCUMENT -> Icons.Outlined.Description
    FileCategory.ARCHIVE -> Icons.Outlined.Archive
    FileCategory.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}
