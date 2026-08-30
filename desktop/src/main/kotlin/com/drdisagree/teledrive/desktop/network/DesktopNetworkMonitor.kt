package com.drdisagree.teledrive.desktop.network

import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.network.NetworkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class DesktopNetworkMonitor : NetworkMonitor {

    private val state = MutableStateFlow(NetworkStatus.UNMETERED)

    override val status: Flow<NetworkStatus> = state

    override fun currentStatus(): NetworkStatus = state.value
}
