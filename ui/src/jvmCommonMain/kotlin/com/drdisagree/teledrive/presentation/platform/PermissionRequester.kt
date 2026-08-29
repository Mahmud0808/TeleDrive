package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.drdisagree.teledrive.core.permissions.AppPermission

fun interface PermissionRequester {

    fun request(permissions: List<AppPermission>, onDone: () -> Unit)
}

val LocalPermissionRequester = staticCompositionLocalOf<PermissionRequester> {
    error("PermissionRequester is not provided")
}
