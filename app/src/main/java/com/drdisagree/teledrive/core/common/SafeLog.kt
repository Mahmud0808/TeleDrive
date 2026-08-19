package com.drdisagree.teledrive.core.common

import android.util.Log
import com.drdisagree.teledrive.BuildConfig

/**
 * Logging facade. Debug logs are stripped in release builds and messages must
 * never contain phone numbers, API credentials, or file contents.
 */
object SafeLog {

    @Volatile
    var verbose: Boolean = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (verbose) Log.d(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
