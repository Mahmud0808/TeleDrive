package com.drdisagree.teledrive.core.telegram

import androidx.core.net.toUri

/**
 * Reads the links proxies are shared as: `tg://proxy`, `tg://socks` and the
 * `t.me` forms of both. Typing host and port by hand still works.
 */
object ProxyLink {

    fun parse(link: String): TelegramProxy? {
        val uri = runCatching { link.trim().toUri() }.getOrNull() ?: return null
        val kind = when {
            uri.scheme.equals("tg", ignoreCase = true) -> uri.host
            uri.host.equals("t.me", ignoreCase = true) ||
                    uri.host.equals("telegram.me", ignoreCase = true) ->
                uri.path?.trim('/')

            else -> null
        }

        val host = uri.getQueryParameter("server")?.trim().orEmpty()
        val port = uri.getQueryParameter("port")?.trim()?.toIntOrNull() ?: return null
        if (host.isEmpty() || port !in MIN_PORT..MAX_PORT) return null

        return when (kind?.lowercase()) {
            "proxy" -> uri.getQueryParameter("secret")?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    TelegramProxy(
                        type = TelegramProxyType.MTPROTO,
                        host = host,
                        port = port,
                        secret = it
                    )
                }

            "socks" -> TelegramProxy(
                type = TelegramProxyType.SOCKS5,
                host = host,
                port = port,
                username = uri.getQueryParameter("user")?.takeIf { it.isNotBlank() },
                password = uri.getQueryParameter("pass")?.takeIf { it.isNotBlank() }
            )

            else -> null
        }
    }

    const val MIN_PORT = 1
    const val MAX_PORT = 65535
}
