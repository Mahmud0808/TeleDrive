package com.drdisagree.teledrive.core.telegram

/**
 * Held by the app, not TDLib: an auth reset wipes TDLib's database and would lose the only working
 * route.
 */
data class TelegramProxy(
    val type: TelegramProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val secret: String? = null
)
