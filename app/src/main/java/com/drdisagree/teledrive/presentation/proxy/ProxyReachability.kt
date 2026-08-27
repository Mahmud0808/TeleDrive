package com.drdisagree.teledrive.presentation.proxy

/** Whether Telegram answered the last time this route was tried. */
enum class ProxyReachability {
    TESTING,
    ANSWERED,
    REACHABLE,
    UNREACHABLE
}
