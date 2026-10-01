package com.drdisagree.teledrive.desktop.permissions

import com.drdisagree.teledrive.core.permissions.AppPermission
import com.drdisagree.teledrive.core.permissions.PermissionChecker

class DesktopPermissionChecker : PermissionChecker {

    override fun isGranted(permission: AppPermission): Boolean = true

    override fun hasAllFilesAccess(): Boolean = true

    override fun statuses(): Map<AppPermission, Boolean> =
        AppPermission.entries.associateWith { true }

    override fun missingCritical(): List<AppPermission> = emptyList()

    override fun isRequestable(permission: AppPermission): Boolean = false
}
