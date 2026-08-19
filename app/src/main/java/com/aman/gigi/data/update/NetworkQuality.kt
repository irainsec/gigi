package com.aman.gigi.data.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * What the connection can actually do, and what the downloader should do about it.
 *
 * The updater used to open eight streams no matter what. On a good link that's right —
 * the tunnel throttles per-connection, so eight of them is eight times the throughput.
 * On a weak mobile link it's actively harmful: the same narrow pipe gets split eight
 * ways, every stream crawls, and any one of them stalling past the read timeout fails
 * a chunk and costs a retry. Fewer, longer-lived streams finish sooner there.
 */
object NetworkQuality {
    private const val TAG = "NetworkQuality"

    data class Snapshot(
        val online: Boolean,
        /** True on mobile data (or a hotspot) — i.e. bytes may cost the user money. */
        val metered: Boolean,
        val wifi: Boolean,
        /** The OS's estimate of downstream bandwidth; 0 when it won't say. */
        val downKbps: Int,
        val streams: Int,
        val readTimeoutMs: Int
    )

    fun of(context: Context): Snapshot {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return Snapshot(true, false, false, 0, DEFAULT_STREAMS, DEFAULT_READ_TIMEOUT_MS)

        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            ?: return Snapshot(false, false, false, 0, 1, SLOW_READ_TIMEOUT_MS)

        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val kbps = runCatching { caps.linkDownstreamBandwidthKbps }.getOrDefault(0)

        val streams = when {
            kbps <= 0 -> if (wifi) DEFAULT_STREAMS else 4   // no estimate: trust the transport
            kbps < 500 -> 2                                  // 2G / edge-of-coverage
            kbps < 2_000 -> 3
            kbps < 8_000 -> 6
            else -> DEFAULT_STREAMS
        }
        val readTimeout = when {
            kbps in 1 until 2_000 -> SLOW_READ_TIMEOUT_MS
            else -> DEFAULT_READ_TIMEOUT_MS
        }

        Log.i(TAG, "link: wifi=$wifi metered=$metered ${kbps}kbps → $streams streams")
        return Snapshot(online, metered, wifi, kbps, streams, readTimeout)
    }

    fun isOnline(context: Context) = of(context).online

    /**
     * Suspends until there's a usable connection, or [timeoutMs] elapses.
     *
     * This is what turns "you walked into a lift" from a failed update into a pause.
     * The part files are already on disk, so whatever comes back resumes from there.
     *
     * @return true if a suitable network arrived (or was already there).
     */
    suspend fun awaitOnline(
        context: Context,
        timeoutMs: Long,
        requireUnmetered: Boolean = false
    ): Boolean {
        val now = of(context)
        if (now.online && (!requireUnmetered || !now.metered)) return true

        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .apply {
                if (requireUnmetered) addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            .build()

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                // Registered callbacks are a process-wide resource, and this can be
                // entered once per chunk retry — so it has to be released on every exit
                // path, not just cancellation.
                val done = java.util.concurrent.atomic.AtomicBoolean(false)
                lateinit var callback: ConnectivityManager.NetworkCallback
                fun finish(result: Boolean) {
                    if (!done.compareAndSet(false, true)) return
                    runCatching { cm.unregisterNetworkCallback(callback) }
                    if (cont.isActive) cont.resume(result)
                }
                callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = finish(true)
                }
                runCatching { cm.registerNetworkCallback(request, callback) }
                    .onFailure { finish(false) }
                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) {
                        runCatching { cm.unregisterNetworkCallback(callback) }
                    }
                }
            }
        } ?: false
    }

    private const val DEFAULT_STREAMS = 8
    private const val DEFAULT_READ_TIMEOUT_MS = 45_000
    private const val SLOW_READ_TIMEOUT_MS = 120_000
}
