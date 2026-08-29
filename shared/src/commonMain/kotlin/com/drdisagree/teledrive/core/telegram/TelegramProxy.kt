package com.drdisagree.teledrive.core.telegram

/**
 * A route to Telegram for networks that block it directly. Held by the app
 * rather than by TDLib: the session database is wiped on an auth reset, and
 * losing the only usable route would leave the account unreachable.
 */
data class TelegramProxy(
    val type: TelegramProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val secret: String? = null
)
