package com.drdisagree.teledrive.presentation.settings

import org.jetbrains.compose.resources.StringResource
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
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.settings_section_about_subtitle
import com.drdisagree.teledrive.resources.settings_section_about_title
import com.drdisagree.teledrive.resources.settings_section_account_subtitle
import com.drdisagree.teledrive.resources.settings_section_account_title
import com.drdisagree.teledrive.resources.settings_section_advanced_subtitle
import com.drdisagree.teledrive.resources.settings_section_advanced_title
import com.drdisagree.teledrive.resources.settings_section_appearance_subtitle
import com.drdisagree.teledrive.resources.settings_section_appearance_title
import com.drdisagree.teledrive.resources.settings_section_backup_subtitle
import com.drdisagree.teledrive.resources.settings_section_backup_title
import com.drdisagree.teledrive.resources.settings_section_notifications_subtitle
import com.drdisagree.teledrive.resources.settings_section_notifications_title
import com.drdisagree.teledrive.resources.settings_section_permissions_subtitle
import com.drdisagree.teledrive.resources.settings_section_permissions_title
import com.drdisagree.teledrive.resources.settings_section_playback_subtitle
import com.drdisagree.teledrive.resources.settings_section_playback_title
import com.drdisagree.teledrive.resources.settings_section_security_subtitle
import com.drdisagree.teledrive.resources.settings_section_security_title
import com.drdisagree.teledrive.resources.settings_section_storage_subtitle
import com.drdisagree.teledrive.resources.settings_section_storage_title

enum class SettingsSectionType(
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val icon: ImageVector
) {
    ACCOUNT(
        Res.string.settings_section_account_title,
        Res.string.settings_section_account_subtitle,
        Icons.Filled.AccountCircle
    ),
    BACKUP(
        Res.string.settings_section_backup_title,
        Res.string.settings_section_backup_subtitle,
        Icons.Filled.CloudUpload
    ),
    STORAGE(
        Res.string.settings_section_storage_title,
        Res.string.settings_section_storage_subtitle,
        Icons.Filled.Storage
    ),
    PERMISSIONS(
        Res.string.settings_section_permissions_title,
        Res.string.settings_section_permissions_subtitle,
        Icons.Filled.Shield
    ),
    SECURITY(
        Res.string.settings_section_security_title,
        Res.string.settings_section_security_subtitle,
        Icons.Filled.Lock
    ),
    APPEARANCE(
        Res.string.settings_section_appearance_title,
        Res.string.settings_section_appearance_subtitle,
        Icons.Filled.Palette
    ),
    PLAYBACK(
        Res.string.settings_section_playback_title,
        Res.string.settings_section_playback_subtitle,
        Icons.Filled.PlayCircle
    ),
    NOTIFICATIONS(
        Res.string.settings_section_notifications_title,
        Res.string.settings_section_notifications_subtitle,
        Icons.Filled.Notifications
    ),
    ADVANCED(
        Res.string.settings_section_advanced_title,
        Res.string.settings_section_advanced_subtitle,
        Icons.Filled.Tune
    ),
    ABOUT(
        Res.string.settings_section_about_title,
        Res.string.settings_section_about_subtitle,
        Icons.Filled.Info
    )
}
