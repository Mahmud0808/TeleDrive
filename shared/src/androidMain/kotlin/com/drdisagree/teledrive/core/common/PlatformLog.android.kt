package com.drdisagree.teledrive.core.common

import android.util.Log

internal actual fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
}

internal actual fun logWarn(tag: String, message: String, throwable: Throwable?) {
    Log.w(tag, message, throwable)
}

internal actual fun logError(tag: String, message: String, throwable: Throwable?) {
    Log.e(tag, message, throwable)
}
