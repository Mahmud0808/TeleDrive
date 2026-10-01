package com.drdisagree.teledrive.core.proxy

/**
 * [REACHABLE] only proves the proxy accepted the connection; MTProto's obfuscated handshake hides
 * the rest.
 */
enum class ProxyProbeResult {
    ANSWERED,
    REACHABLE,
    UNREACHABLE
}
