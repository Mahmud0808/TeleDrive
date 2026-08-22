package com.drdisagree.teledrive.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.drdisagree.teledrive.R

enum class SettingsSectionType(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector
) {
    ACCOUNT(
        R.string.settings_section_account_title,
        R.string.settings_section_account_subtitle,
        Icons.Filled.AccountCircle
    ),
    BACKUP(
        R.string.settings_section_backup_title,
        R.string.settings_section_backup_subtitle,
        Icons.Filled.CloudUpload
    ),
    STORAGE(
        R.string.settings_section_storage_title,
        R.string.settings_section_storage_subtitle,
        Icons.Filled.Storage
    ),
    PERMISSIONS(
        R.string.settings_section_permissions_title,
        R.string.settings_section_permissions_subtitle,
        Icons.Filled.Shield
    ),
    SECURITY(
        R.string.settings_section_security_title,
        R.string.settings_section_security_subtitle,
        Icons.Filled.Lock
    ),
    APPEARANCE(
        R.string.settings_section_appearance_title,
        R.string.settings_section_appearance_subtitle,
        Icons.Filled.Palette
    ),
    PLAYBACK(
        R.string.settings_section_playback_title,
        R.string.settings_section_playback_subtitle,
        Icons.Filled.PlayCircle
    ),
    NOTIFICATIONS(
        R.string.settings_section_notifications_title,
        R.string.settings_section_notifications_subtitle,
        Icons.Filled.Notifications
    ),
    ADVANCED(
        R.string.settings_section_advanced_title,
        R.string.settings_section_advanced_subtitle,
        Icons.Filled.Tune
    ),
    ABOUT(
        R.string.settings_section_about_title,
        R.string.settings_section_about_subtitle,
        Icons.Filled.Info
    )
}
