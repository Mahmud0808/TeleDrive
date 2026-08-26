package com.drdisagree.teledrive.presentation.proxy

import com.drdisagree.teledrive.domain.model.ProxyServer

data class ProxyUiState(
    val proxies: List<ProxyServer> = emptyList(),
    val enabled: Boolean = false,
    val loading: Boolean = true,
    val reachability: Map<String, ProxyReachability> = emptyMap(),
    val testable: Boolean = false
)
