package me.luzility.muuusic.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Connectivity(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val networks = mutableMapOf<Network, Boolean>()

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                update(network, capabilities.hasInternet())
            }

            override fun onLost(network: Network) {
                synchronized(networks) { networks.remove(network) }
                publish()
            }
        })
    }

    private fun update(network: Network, internet: Boolean) {
        synchronized(networks) { networks[network] = internet }
        publish()
    }

    private fun publish() {
        _online.value = synchronized(networks) { networks.values.any { it } }
    }

    private fun currentlyOnline(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasInternet() == true

    private fun NetworkCapabilities.hasInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
