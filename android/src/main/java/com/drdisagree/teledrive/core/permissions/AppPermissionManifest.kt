package com.drdisagree.teledrive.core.permissions

import android.Manifest
import android.os.Build

/** Null when the permission is not a runtime permission on this device. */
val AppPermission.manifestPermission: String?
    get() = when (this) {
        AppPermission.MEDIA_IMAGES -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ->
                Manifest.permission.WRITE_EXTERNAL_STORAGE

            else -> Manifest.permission.READ_EXTERNAL_STORAGE
        }

        AppPermission.MEDIA_VIDEO -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_VIDEO
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ->
                Manifest.permission.WRITE_EXTERNAL_STORAGE

            else -> Manifest.permission.READ_EXTERNAL_STORAGE
        }

        AppPermission.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {
                null
            }

        AppPermission.ALL_FILES,
        AppPermission.UNRESTRICTED_BATTERY -> null
    }
