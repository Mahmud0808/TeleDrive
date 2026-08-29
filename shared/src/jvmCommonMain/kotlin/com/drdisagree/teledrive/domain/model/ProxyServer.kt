package com.drdisagree.teledrive.domain.model

import com.drdisagree.teledrive.core.telegram.TelegramProxyType

data class ProxyServer(
    val id: String,
    val label: String,
    val type: TelegramProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val secret: String? = null,
    val isActive: Boolean = false
)
