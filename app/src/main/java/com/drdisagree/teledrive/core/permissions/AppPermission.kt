package com.drdisagree.teledrive.core.permissions

import android.Manifest
import android.os.Build
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.permission_all_files_rationale
import com.drdisagree.teledrive.resources.permission_all_files_title
import com.drdisagree.teledrive.resources.permission_notifications_rationale
import com.drdisagree.teledrive.resources.permission_notifications_title
import com.drdisagree.teledrive.resources.permission_photos_rationale
import com.drdisagree.teledrive.resources.permission_photos_title
import com.drdisagree.teledrive.resources.permission_videos_rationale
import com.drdisagree.teledrive.resources.permission_videos_title
import org.jetbrains.compose.resources.StringResource

/**
 * Permissions the app can ask for, with the reason shown to the user.
 * [critical] marks the ones without which automatic backup cannot work.
 */
enum class AppPermission(
    val titleRes: StringResource,
    val rationaleRes: StringResource,
    val critical: Boolean
) {
    MEDIA_IMAGES(
        titleRes = Res.string.permission_photos_title,
        rationaleRes = Res.string.permission_photos_rationale,
        critical = true
    ),
    MEDIA_VIDEO(
        titleRes = Res.string.permission_videos_title,
        rationaleRes = Res.string.permission_videos_rationale,
        critical = true
    ),
    NOTIFICATIONS(
        titleRes = Res.string.permission_notifications_title,
        rationaleRes = Res.string.permission_notifications_rationale,
        critical = false
    ),
    ALL_FILES(
        titleRes = Res.string.permission_all_files_title,
        rationaleRes = Res.string.permission_all_files_rationale,
        critical = true
    );

    /** Null when the permission is not a runtime permission on this device. */
    val manifestPermission: String?
        get() = when (this) {
            MEDIA_IMAGES ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

            MEDIA_VIDEO ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

            NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else {
                    null
                }

            ALL_FILES -> null
        }

    /** Special access granted from a system settings screen, not a dialog. */
    val isSpecialAccess: Boolean get() = this == ALL_FILES
}
