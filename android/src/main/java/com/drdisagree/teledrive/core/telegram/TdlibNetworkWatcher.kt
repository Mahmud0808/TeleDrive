package com.drdisagree.teledrive.core.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.drdisagree.teledrive.core.common.SafeLog
import java.util.concurrent.atomic.AtomicBoolean

class TdlibNetworkWatcher(
    context: Context,
    private val onChange: () -> Unit
) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val started = AtomicBoolean(false)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onChange()

        override fun onLost(network: Network) = onChange()
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
    }
}
