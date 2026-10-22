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
    private var current: Network? = null

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(this@Connectivity) { current = network }
                _online.value = manager.getNetworkCapabilities(network)?.hasInternet() == true
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                synchronized(this@Connectivity) { current = network }
                _online.value = capabilities.hasInternet()
            }

            override fun onLost(network: Network) {
                val wasCurrent = synchronized(this@Connectivity) {
                    (current == network).also { if (it) current = null }
                }
                if (wasCurrent) _online.value = false
            }
        })
    }

    private fun currentlyOnline(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasInternet() == true

    private fun NetworkCapabilities.hasInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
