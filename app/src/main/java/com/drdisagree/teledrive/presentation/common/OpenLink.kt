package com.drdisagree.teledrive.presentation.common

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Hands a URL to whatever the device opens it with, tolerating there being none. */
fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
