package it.cerrone.dailyhub

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Looks for the PC announcing itself as `_nexus._tcp` on the home network. */
object PcFinder {
    suspend fun find(context: Context): String? = withContext(Dispatchers.Main) { withTimeoutOrNull(4_000) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return@withTimeoutOrNull null
        suspendCancellableCoroutine<String?> { cont ->
            lateinit var listener: NsdManager.DiscoveryListener
            listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = info.host?.hostAddress ?: return
                            val url = if (host.contains(':')) "http://[$host]:${info.port}" else "http://$host:${info.port}"
                            if (cont.isActive) cont.resume(url)
                            runCatching { nsd.stopServiceDiscovery(listener) }
                        }
                    })
                }
            }
            cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
            nsd.discoverServices("_nexus._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        }
    } }
}
