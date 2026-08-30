package com.drdisagree.teledrive.core.common

/**
 * Logging facade. Debug logs are stripped in release builds and messages must
 * never contain phone numbers, API credentials, or file contents.
 */
object SafeLog {

    var verbose: Boolean = false

    fun d(tag: String, message: String) {
        if (verbose) logDebug(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        logWarn(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        logError(tag, message, throwable)
    }
}
