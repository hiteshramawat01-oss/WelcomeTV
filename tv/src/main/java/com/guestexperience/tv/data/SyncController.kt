package com.guestexperience.tv.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.guestexperience.shared.sync.SyncProtocol
import com.guestexperience.shared.sync.TvSyncServer
import kotlinx.coroutines.CoroutineScope

/**
 * Runs the local sync server and announces it on the Wi-Fi (DNS-SD) only while the app is in the
 * foreground. Nothing listens on the network when the guest has left the app.
 */
class SyncController(
    context: Context,
    private val server: TvSyncServer,
    private val tvId: String,
    private val serviceName: () -> String,
    private val scope: CoroutineScope,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null

    fun start() {
        if (registration != null) return
        val port = try {
            server.start(scope)
        } catch (_: Exception) {
            return
        }
        val info = NsdServiceInfo().apply {
            serviceName = this@SyncController.serviceName().take(60)
            serviceType = SyncProtocol.SERVICE_TYPE
            setPort(port)
            setAttribute(SyncProtocol.TXT_TV_ID, tvId)
            setAttribute(SyncProtocol.TXT_VERSION, SyncProtocol.VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        server.stop()
    }
}
