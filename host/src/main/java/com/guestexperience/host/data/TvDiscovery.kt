package com.guestexperience.host.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.guestexperience.shared.sync.SamsungChannelDialer
import com.guestexperience.shared.sync.SyncProtocol
import com.guestexperience.shared.sync.TvKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque
import java.util.concurrent.Executors

/** A TV announcing itself on this Wi-Fi: the Android TV app, or any Samsung smart TV. */
data class FoundTv(
    val serviceName: String,
    val tvId: String,
    val host: String,
    val port: Int,
    val kind: TvKind = TvKind.ANDROID,
)

/**
 * Finds TVs with DNS-SD (NsdManager): Guest Experience TV announces `_guesttv._tcp` while it is open,
 * and Samsung TVs always announce their multiscreen service `_samsungmsf._tcp` (the Guest Welcome app
 * is started on demand when the phone connects). Resolves one service at a time, as older Android requires.
 */
class TvDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _tvs = MutableStateFlow<List<FoundTv>>(emptyList())
    val tvs: StateFlow<List<FoundTv>> = _tvs.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val serviceTypes = mapOf(
        SyncProtocol.SERVICE_TYPE to TvKind.ANDROID,
        SamsungChannelDialer.SERVICE_TYPE to TvKind.SAMSUNG,
    )
    private val listeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private val active = mutableSetOf<String>()
    private val pending = ArrayDeque<Pair<NsdServiceInfo, TvKind>>()
    private var resolving = false
    private val executor = Executors.newSingleThreadExecutor()

    @Synchronized
    fun start() {
        serviceTypes.forEach { (type, kind) ->
            if (listeners.containsKey(type)) return@forEach
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = searching(type, true)
                override fun onDiscoveryStopped(serviceType: String) = searching(type, false)
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = searching(type, false)
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceFound(info: NsdServiceInfo) = enqueue(info, kind)
                override fun onServiceLost(info: NsdServiceInfo) {
                    _tvs.update { list -> list.filterNot { it.kind == kind && it.serviceName == info.serviceName } }
                }
            }
            listeners[type] = l
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l) }
                .onFailure { listeners.remove(type) }
        }
    }

    @Synchronized
    fun stop() {
        listeners.values.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        pending.clear()
        resolving = false
    }

    /** Forget what was found and search again, e.g. after the TV app was reopened. Added-by-address TVs stay. */
    fun refresh() {
        stop()
        _tvs.value = manual.values.toList()
        start()
    }

    /** TVs added by IP address (or remembered from pairing) for networks where DNS-SD doesn't get through. */
    private val manual = java.util.concurrent.ConcurrentHashMap<String, FoundTv>()

    fun addManual(tv: FoundTv) {
        manual[tv.tvId] = tv
        _tvs.update { list -> list.filterNot { it.tvId == tv.tvId } + tv }
    }

    @Synchronized
    private fun searching(type: String, on: Boolean) {
        if (on) active += type else active -= type
        _searching.value = active.isNotEmpty()
    }

    @Synchronized
    private fun enqueue(info: NsdServiceInfo, kind: TvKind) {
        pending.add(info to kind)
        resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        if (resolving) return
        val (next, kind) = pending.poll() ?: return
        resolving = true
        resolve(next, kind)
    }

    @Synchronized
    private fun done() {
        resolving = false
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo, kind: TvKind) {
        if (Build.VERSION.SDK_INT >= 34) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = done()
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    publish(serviceInfo, serviceInfo.hostAddresses.firstOrNull()?.hostAddress, kind)
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
                    publish(serviceInfo, serviceInfo.host?.hostAddress, kind)
                    done()
                }
            })
        }
    }

    private fun publish(info: NsdServiceInfo, address: String?, kind: TvKind) {
        val host = address ?: return
        fun txt(key: String) = info.attributes[key]?.toString(Charsets.UTF_8)
        val found = when (kind) {
            TvKind.ANDROID -> {
                val tvId = txt(SyncProtocol.TXT_TV_ID) ?: return
                FoundTv(info.serviceName, tvId, host, info.port, kind)
            }
            // Samsung's record: id=uuid:…, fn=[TV] Samsung 4 Series (32). The Guest Welcome app uses the same id.
            TvKind.SAMSUNG -> {
                val tvId = txt("id") ?: info.serviceName
                val name = (txt("fn") ?: "Samsung TV").removePrefix("[TV]").trim()
                FoundTv(name, tvId, host, info.port.takeIf { it > 0 } ?: SamsungChannelDialer.DEFAULT_PORT, kind)
            }
        }
        // A fresh DNS-SD answer wins over a remembered address (the TV's IP may have changed).
        if (manual.containsKey(found.tvId)) manual[found.tvId] = found
        _tvs.update { list -> list.filterNot { it.tvId == found.tvId } + found }
    }
}
