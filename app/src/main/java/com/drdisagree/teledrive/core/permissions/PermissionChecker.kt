package com.drdisagree.teledrive.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun isGranted(permission: AppPermission): Boolean = when {
        permission.isSpecialAccess -> hasAllFilesAccess()
        else -> permission.manifestPermission?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: true
    }

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()

    fun statuses(): Map<AppPermission, Boolean> =
        AppPermission.entries.associateWith(::isGranted)

    fun missingCritical(): List<AppPermission> =
        AppPermission.entries.filter { it.critical && !isGranted(it) }
}
