package com.drdisagree.teledrive.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NetworkMonitor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val status: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(statusOf(networkCapabilities))
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        trySend(currentStatus())
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()

    override fun currentStatus(): NetworkStatus {
        val capabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
            ?: return NetworkStatus.UNAVAILABLE
        return statusOf(capabilities)
    }

    private fun statusOf(capabilities: NetworkCapabilities): NetworkStatus = when {
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ->
            NetworkStatus.UNAVAILABLE

        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ->
            NetworkStatus.UNMETERED

        else -> NetworkStatus.METERED
    }
}
