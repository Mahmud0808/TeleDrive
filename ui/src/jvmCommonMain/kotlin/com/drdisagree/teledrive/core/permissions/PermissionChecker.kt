package com.drdisagree.teledrive.core.permissions

/** Answers which of the app's permissions are currently granted. */
interface PermissionChecker {

    fun isGranted(permission: AppPermission): Boolean

    fun hasAllFilesAccess(): Boolean

    fun statuses(): Map<AppPermission, Boolean>

    fun missingCritical(): List<AppPermission>

    fun isRequestable(permission: AppPermission): Boolean
}
