package com.inoviq.videodownloader.videosaver.storysaver.ads.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * Monitors network state using ConnectivityManager with NET_CAPABILITY_VALIDATED.
 * Emits reconnect callbacks when internet is restored so pending/missing ads reload.
 */
class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val reconnectListeners = mutableListOf<() -> Unit>()
    private var wasOffline = false

    init {
        registerNetworkCallback()
    }

    /**
     * Checks if current connection is fully validated for internet access.
     */
    val isConnected: Boolean
        get() {
            val cm = connectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    /**
     * Registers a callback triggered when device reconnects after being offline.
     */
    fun addReconnectListener(listener: () -> Unit) {
        synchronized(reconnectListeners) {
            reconnectListeners.add(listener)
        }
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network became available.")
                    if (wasOffline) {
                        wasOffline = false
                        Log.d(TAG, "Reconnected to internet. Triggering reload callbacks.")
                        notifyReconnect()
                    }
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "Network lost.")
                    wasOffline = true
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun notifyReconnect() {
        mainHandler.post {
            synchronized(reconnectListeners) {
                reconnectListeners.forEach { it.invoke() }
            }
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
