package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Platforms without a secure lock confirm immediately, or there would be no way to recover. */
fun interface DeviceOwnerGate {

    fun require(
        title: String,
        subtitle: String,
        onDenied: (String?) -> Unit,
        onConfirmed: () -> Unit
    )
}

val LocalDeviceOwnerGate = staticCompositionLocalOf<DeviceOwnerGate> {
    error("DeviceOwnerGate is not provided")
}
