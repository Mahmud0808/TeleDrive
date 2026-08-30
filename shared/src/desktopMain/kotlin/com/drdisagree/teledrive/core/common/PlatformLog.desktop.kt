package com.drdisagree.teledrive.core.common

internal actual fun logDebug(tag: String, message: String) {
    println("D/$tag: $message")
}

internal actual fun logWarn(tag: String, message: String, throwable: Throwable?) {
    System.err.println("W/$tag: $message")
    throwable?.printStackTrace()
}

internal actual fun logError(tag: String, message: String, throwable: Throwable?) {
    System.err.println("E/$tag: $message")
    throwable?.printStackTrace()
}
