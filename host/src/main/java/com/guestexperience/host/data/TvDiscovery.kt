package com.guestexperience.host.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.guestexperience.shared.sync.SyncProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque
import java.util.concurrent.Executors

/** A Guest Experience TV announcing itself on this Wi-Fi. */
data class FoundTv(
    val serviceName: String,
    val tvId: String,
    val host: String,
    val port: Int,
)

/** Finds TVs with DNS-SD (NsdManager). Resolves one service at a time, as older Android requires. */
class TvDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _tvs = MutableStateFlow<List<FoundTv>>(emptyList())
    val tvs: StateFlow<List<FoundTv>> = _tvs.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val executor = Executors.newSingleThreadExecutor()

    @Synchronized
    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { _searching.value = true }
            override fun onDiscoveryStopped(serviceType: String) { _searching.value = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { _searching.value = false }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = enqueue(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _tvs.update { list -> list.filterNot { it.serviceName == info.serviceName } }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(SyncProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { listener = null }
    }

    @Synchronized
    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        pending.clear()
        resolving = false
    }

    /** Forget what was found and search again, e.g. after the TV app was reopened. */
    fun refresh() {
        stop()
        _tvs.value = emptyList()
        start()
    }

    @Synchronized
    private fun enqueue(info: NsdServiceInfo) {
        pending.add(info)
        resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        if (resolving) return
        val next = pending.poll() ?: return
        resolving = true
        resolve(next)
    }

    @Synchronized
    private fun done() {
        resolving = false
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= 34) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = done()
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    publish(serviceInfo, serviceInfo.hostAddresses.firstOrNull()?.hostAddress)
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = done()
            }
            runCatching { nsd.registerServiceInfoCallback(info, executor, callback) }.onFailure { done() }
        } else {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done()
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    publish(serviceInfo, serviceInfo.host?.hostAddress)
                    done()
                }
            })
        }
    }

    private fun publish(info: NsdServiceInfo, address: String?) {
        val tvId = info.attributes[SyncProtocol.TXT_TV_ID]?.toString(Charsets.UTF_8) ?: return
        val host = address ?: return
        val found = FoundTv(info.serviceName, tvId, host, info.port)
        _tvs.update { list -> list.filterNot { it.tvId == tvId } + found }
    }
}
