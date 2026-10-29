package com.gdimitria.notificationreminders.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.gdimitria.notificationreminders.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Follows the connected Wi-Fi network and hands every change to [WifiRules]. Runs while a long-lived
 * component is alive (notification listener, accessibility service) or the app is on screen.
 * Android only reveals the network name to apps with location permission, in the background only with
 * "Allow all the time".
 */
object WifiWatcher {

    private const val UNKNOWN_SSID = "<unknown ssid>"

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var users = 0
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Whether the rules saw the network state since [start]; without Wi-Fi no callback arrives at all. */
    private var evaluated = false

    private val _ssid = MutableStateFlow<String?>(null)

    /** Name of the connected Wi-Fi network, or null when not connected or not readable. */
    val ssid: StateFlow<String?> = _ssid.asStateFlow()

    fun start(context: Context) {
        users++
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val networkCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    post((capabilities.transportInfo as? WifiInfo)?.ssid)

                override fun onLost(network: Network) = post(null)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    post(legacySsid(app))

                override fun onLost(network: Network) = post(null)
            }
        }
        callback = networkCallback
        evaluated = false
        runCatching { connectivity.registerNetworkCallback(request, networkCallback) }
        // No Wi-Fi at all: release a rule whose network was left while the app was not running.
        handler.postDelayed({ if (!evaluated) post(null) }, 3000)
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        val app = appContext ?: return
        callback?.let { runCatching { app.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        callback = null
        appContext = null
    }

    /** Names of the Wi-Fi networks in range, strongest first, from the last scan (needs location access). */
    fun nearby(context: Context): List<String> {
        val wifi = context.getSystemService(WifiManager::class.java)
        val results = try {
            wifi.scanResults.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        return results
            .sortedByDescending { it.level }
            .mapNotNull { result ->
                val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.wifiSsid?.toString()
                } else {
                    @Suppress("DEPRECATION")
                    result.SSID
                }
                raw?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
            }
            .distinct()
    }

    /** Asks for a fresh scan; Android limits how often apps may scan, so the last results are used meanwhile. */
    @Suppress("DEPRECATION")
    fun requestScan(context: Context) {
        runCatching { context.getSystemService(WifiManager::class.java).startScan() }
    }

    @Suppress("DEPRECATION")
    private fun legacySsid(context: Context): String? =
        context.getSystemService(WifiManager::class.java).connectionInfo?.ssid

    /** Network callbacks run on a binder thread; rules are applied on the main thread. */
    private fun post(raw: String?) {
        val name = raw?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
        handler.post {
            if (evaluated && name == _ssid.value) return@post
            evaluated = true
            _ssid.value = name
            appContext?.let { context ->
                if (name != null) Store.get(context).updateSoundProfiles { it.withKnownSsid(name) }
                WifiRules.onNetworkChanged(context, name)
                // Reminders pause on networks where the reminder profile keeps them quiet, and resume after.
                ReminderEngine.onSettingsChanged(context)
            }
        }
    }
}
