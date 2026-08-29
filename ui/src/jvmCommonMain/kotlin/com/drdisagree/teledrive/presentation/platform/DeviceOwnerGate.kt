package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Asks for the device owner before a sensitive change. Platforms without a
 * secure lock confirm immediately, otherwise there would be no way to recover.
 */
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
