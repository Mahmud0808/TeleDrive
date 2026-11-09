package com.drdisagree.teledrive.core.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.drdisagree.teledrive.core.common.SafeLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope

class TdlibNetworkWatcher(
    context: Context,
    scope: CoroutineScope,
    onChange: () -> Unit
) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val started = AtomicBoolean(false)
    private val settler = NetworkSettler<Network>(scope, SETTLE_MS, onChange)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = settler.available(network)

        override fun onLost(network: Network) = settler.lost(network)
    }

    fun start() {
        val connectivity = manager ?: return
        if (!started.compareAndSet(false, true)) return
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onFailure {
                started.set(false)
                SafeLog.w(TAG, "Could not watch network changes", it)
            }
    }

    private companion object {
        const val TAG = "TdlibNetworkWatcher"
        const val SETTLE_MS = 10_000L
    }
}
