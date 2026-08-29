package com.drdisagree.teledrive.core.network

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    val status: Flow<NetworkStatus>
    fun currentStatus(): NetworkStatus
}
