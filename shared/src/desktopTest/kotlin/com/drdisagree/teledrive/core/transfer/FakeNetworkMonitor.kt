package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.network.NetworkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeNetworkMonitor(initial: NetworkStatus) : NetworkMonitor {
    private val state = MutableStateFlow(initial)
    override val status: Flow<NetworkStatus> = state
    override fun currentStatus(): NetworkStatus = state.value
}
