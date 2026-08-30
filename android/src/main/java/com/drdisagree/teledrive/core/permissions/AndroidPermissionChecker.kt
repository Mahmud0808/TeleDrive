package com.drdisagree.teledrive.core.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

class AndroidPermissionChecker(
    private val context: Context
) : PermissionChecker {

    override fun isGranted(permission: AppPermission): Boolean = when {
        permission.isSpecialAccess -> hasAllFilesAccess()
        else -> permission.manifestPermission?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: true
    }

    override fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()

    override fun statuses(): Map<AppPermission, Boolean> =
        AppPermission.entries.associateWith(::isGranted)

    override fun missingCritical(): List<AppPermission> =
        AppPermission.entries.filter { it.critical && !isGranted(it) }

    override fun isRequestable(permission: AppPermission): Boolean =
        permission.manifestPermission != null || permission.isSpecialAccess
}
