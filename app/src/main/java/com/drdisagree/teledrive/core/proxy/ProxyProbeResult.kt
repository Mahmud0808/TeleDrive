package com.drdisagree.teledrive.core.proxy

/**
 * How far a check got. [ANSWERED] means Telegram itself was reached through the
 * proxy; [REACHABLE] means only that the proxy accepted the connection, which is
 * as much as can be proven for MTProto without its obfuscated handshake.
 */
enum class ProxyProbeResult {
    ANSWERED,
    REACHABLE,
    UNREACHABLE
}
