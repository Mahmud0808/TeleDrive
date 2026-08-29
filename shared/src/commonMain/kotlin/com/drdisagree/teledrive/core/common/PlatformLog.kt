package com.drdisagree.teledrive.core.common

internal expect fun logDebug(tag: String, message: String)

internal expect fun logWarn(tag: String, message: String, throwable: Throwable?)

internal expect fun logError(tag: String, message: String, throwable: Throwable?)
