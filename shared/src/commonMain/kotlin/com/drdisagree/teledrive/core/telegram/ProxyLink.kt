package com.drdisagree.teledrive.core.telegram

/**
 * Reads the links proxies are shared as: `tg://proxy`, `tg://socks` and the
 * `t.me` forms of both. Typing host and port by hand still works.
 */
object ProxyLink {

    fun parse(link: String): TelegramProxy? {
        val trimmed = link.trim()
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd)
        val rest = trimmed.substring(schemeEnd + 3)
        val queryStart = rest.indexOf('?')
        val authorityAndPath = if (queryStart >= 0) rest.substring(0, queryStart) else rest
        val query = if (queryStart >= 0) rest.substring(queryStart + 1) else ""
        val slash = authorityAndPath.indexOf('/')
        val authority = if (slash >= 0) authorityAndPath.substring(0, slash) else authorityAndPath
        val path = if (slash >= 0) authorityAndPath.substring(slash) else ""
        val parameters = query.split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) null else pair.substring(0, eq) to urlDecode(pair.substring(eq + 1))
        }.toMap()

        val kind = when {
            scheme.equals("tg", ignoreCase = true) -> authority
            authority.equals("t.me", ignoreCase = true) ||
                    authority.equals("telegram.me", ignoreCase = true) ->
                path.trim('/')

            else -> null
        }

        val host = parameters["server"]?.trim().orEmpty()
        val port = parameters["port"]?.trim()?.toIntOrNull() ?: return null
        if (host.isEmpty() || port !in MIN_PORT..MAX_PORT) return null

        return when (kind?.lowercase()) {
            "proxy" -> parameters["secret"]?.trim()
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
                username = parameters["user"]?.takeIf { it.isNotBlank() },
                password = parameters["pass"]?.takeIf { it.isNotBlank() }
            )

            else -> null
        }
    }

    const val MIN_PORT = 1
    const val MAX_PORT = 65535

    private fun urlDecode(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            val hex = if (char == '%' && index + 3 <= value.length) {
                value.substring(index + 1, index + 3)
            } else {
                null
            }
            val decoded = hex?.toIntOrNull(16)
            when {
                char == '+' -> append(' ')
                decoded != null -> {
                    append(decoded.toChar())
                    index += 2
                }

                else -> append(char)
            }
            index++
        }
    }
}
