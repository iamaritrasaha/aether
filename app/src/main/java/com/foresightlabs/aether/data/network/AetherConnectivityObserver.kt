package com.foresightlabs.aether.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Process-level connectivity state for Aether environment & UI surfaces.
 */
enum class AetherConnectivityState {
    /** Validated Android network and active/ready Telegram connection. */
    ONLINE,

    /** No network, unvalidated network, or TDLib waiting for network -- lights off state. */
    OFFLINE,

    /** Validated Android network, but TDLib is re-establishing session -- environment stays alive. */
    RECONNECTING
}

/**
 * Process-wide single source of truth for internet and Telegram connectivity.
 * Combines Android's [ConnectivityManager] capabilities with TDLib's connection state.
 */
class AetherConnectivityObserver(
    context: Context,
    private val telegram: TelegramClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isAndroidInternetValidated = MutableStateFlow(checkInitialInternet(connectivityManager))
    val isAndroidInternetValidated: StateFlow<Boolean> = _isAndroidInternetValidated.asStateFlow()

    private val _state = MutableStateFlow(
        resolveState(_isAndroidInternetValidated.value, telegram.connection.value)
    )
    val state: StateFlow<AetherConnectivityState> = _state.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerNetworkCallback()
        scope.launch {
            combine(_isAndroidInternetValidated, telegram.connection) { isAndroidOk, tdlibStatus ->
                resolveState(isAndroidOk, tdlibStatus)
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                _isAndroidInternetValidated.value = hasInternet && isValidated
            }

            override fun onLost(network: Network) {
                _isAndroidInternetValidated.value = false
            }

            override fun onUnavailable() {
                _isAndroidInternetValidated.value = false
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Fallback for restricted test/emulated runtimes
        }
    }

    fun unregister() {
        val cm = connectivityManager ?: return
        networkCallback?.let {
            runCatching { cm.unregisterNetworkCallback(it) }
        }
        networkCallback = null
    }

    companion object {
        @Volatile
        private var instance: AetherConnectivityObserver? = null

        fun getInstance(context: Context, telegram: TelegramClient): AetherConnectivityObserver {
            return instance ?: synchronized(this) {
                instance ?: AetherConnectivityObserver(context, telegram).also { instance = it }
            }
        }

        internal fun checkInitialInternet(cm: ConnectivityManager?): Boolean {
            if (cm == null) return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        internal fun resolveState(isAndroidOk: Boolean, tdlibStatus: ConnectionStatus): AetherConnectivityState {
            if (!isAndroidOk || tdlibStatus == ConnectionStatus.WAITING_FOR_NETWORK) {
                return AetherConnectivityState.OFFLINE
            }
            return when (tdlibStatus) {
                ConnectionStatus.READY -> AetherConnectivityState.ONLINE
                ConnectionStatus.CONNECTING,
                ConnectionStatus.CONNECTING_PROXY,
                ConnectionStatus.UPDATING -> AetherConnectivityState.RECONNECTING
                else -> AetherConnectivityState.ONLINE
            }
        }
    }
}
