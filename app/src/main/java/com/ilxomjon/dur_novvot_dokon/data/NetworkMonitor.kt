package com.ilxomjon.dur_novvot_dokon.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Telefon tarmog'i holati. "Online" — internet bor degani, server ko'rinadi degani EMAS. */
data class NetState(val online: Boolean = true, val wifi: Boolean = false, val vpn: Boolean = false)

class NetworkMonitor(private val context: Context) {
    private val _state = MutableStateFlow(NetState())
    val state: StateFlow<NetState> = _state.asStateFlow()
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        refresh(cm)
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { refresh(cm) }
                override fun onLost(network: Network) { refresh(cm) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { refresh(cm) }
            })
        }
    }

    fun refresh(cm: ConnectivityManager? = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager): NetState {
        val s = runCatching {
            val n = cm?.activeNetwork
            val caps = n?.let { cm.getNetworkCapabilities(it) }
            var vpn = false
            cm?.allNetworks?.forEach { net -> if (cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) vpn = true }
            NetState(
                online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
                wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                vpn = vpn,
            )
        }.getOrDefault(NetState())
        _state.value = s
        return s
    }
}
