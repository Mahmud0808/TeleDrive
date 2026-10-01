package com.drdisagree.teledrive.core.permissions

interface PermissionChecker {

    fun isGranted(permission: AppPermission): Boolean

    fun hasAllFilesAccess(): Boolean

    fun statuses(): Map<AppPermission, Boolean>

    fun missingCritical(): List<AppPermission>

    fun isRequestable(permission: AppPermission): Boolean
}
