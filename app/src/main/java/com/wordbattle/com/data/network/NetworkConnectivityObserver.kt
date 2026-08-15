package com.wordbattle.com.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes real internet availability with [ConnectivityManager.NetworkCallback].
 *
 * Exposes a [StateFlow] so the UI can react instantly: gate online actions, show the offline
 * dialog, display a reconnecting banner and re-subscribe Realtime when the link comes back.
 * Offline modes (Computer, fully local pass-and-play) never consult this observer.
 */
class NetworkConnectivityObserver(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Snapshot for one-shot checks made right before an online action. */
    val isCurrentlyOnline: Boolean get() = _isOnline.value

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh()
        }

        override fun onLost(network: Network) {
            refresh()
        }

        override fun onUnavailable() {
            refresh()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            refresh()
        }
    }

    private var registered = false

    /** Starts listening. Safe to call more than once. */
    fun start(scope: CoroutineScope? = null) {
        val manager = connectivityManager ?: return
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { manager.registerNetworkCallback(request, callback) }
            .onSuccess { registered = true }
        refresh()
        // scope is accepted for symmetry with other observers; no coroutine work is required.
        scope?.let { }
    }

    fun stop() {
        val manager = connectivityManager ?: return
        if (!registered) return
        runCatching { manager.unregisterNetworkCallback(callback) }
        registered = false
    }

    /** Re-evaluates the current state, e.g. when the user taps Retry in the offline dialog. */
    fun refresh(): Boolean {
        val online = currentlyOnline()
        _isOnline.value = online
        return online
    }

    private fun currentlyOnline(): Boolean {
        val manager = connectivityManager ?: return false
        val active = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
