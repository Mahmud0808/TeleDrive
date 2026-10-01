package com.drdisagree.teledrive.core.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** The only way back from a permanent denial. */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }
}
