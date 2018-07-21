package be.mygod.dhcpv6client

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import be.mygod.dhcpv6client.App.Companion.app
import be.mygod.dhcpv6client.util.StickyEvent1
import com.crashlytics.android.Crashlytics
import java.io.IOException

class Dhcp6cService : Service() {
    companion object {
        var running = false
        var enabled: Boolean
            get() = BootReceiver.enabled
            set(value) {
                if (value == BootReceiver.enabled) return
                BootReceiver.enabled = value
                if (value && !Dhcp6cService.running) app.startService(Intent(app, Dhcp6cService::class.java))
                else if (!value && Dhcp6cService.running) app.stopService(Intent(app, Dhcp6cService::class.java))
                enabledChanged(value)
            }
        val enabledChanged = StickyEvent1 { enabled }
    }

    private val connectivity by lazy { getSystemService<ConnectivityManager>()!! }
    private val request = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        var registered = false
        val working = HashMap<Network, String>()

        override fun onAvailable(network: Network) {
            val ifname = connectivity.getLinkProperties(network)?.interfaceName ?: return
            if (working.put(network, ifname) == null) try { // prevent re-requesting the same interface
               Dhcp6cManager.startInterface(ifname)
            } catch (e: IOException) {
                e.printStackTrace()
                Crashlytics.logException(e)
                Toast.makeText(this@Dhcp6cService, e.message, Toast.LENGTH_LONG).show()
            }
        }

        override fun onLost(network: Network?) {
            working.remove(network ?: return)
        }

        fun onDhcpv6Configured(iface: String) {
            val network = working.entries.singleOrNull { (_, ifname) -> iface == ifname } ?: return
            Crashlytics.log(Log.INFO, "Dhcp6cService", "Reporting connectivity on network $network ($iface)")
            if (Build.VERSION.SDK_INT >= 23) connectivity.reportNetworkConnectivity(network.key, true)
            else @Suppress("DEPRECATION") connectivity.reportBadNetwork(network.key)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onBind(p0: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!callback.registered) {
            connectivity.allNetworks.forEach {
                callback.working[it] = connectivity.getLinkProperties(it)?.interfaceName ?: return@forEach
            }
            try {
                Dhcp6cManager.startDaemon(callback.working.values)
                Dhcp6cManager.dhcpv6Configured[this] = callback::onDhcpv6Configured
                connectivity.registerNetworkCallback(request, callback)
                callback.registered = true
            } catch (e: IOException) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                Crashlytics.logException(e)
                e.printStackTrace()
                stopSelf(startId)
                enabled = false
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (callback.registered) {
            Dhcp6cManager.dhcpv6Configured -= this
            connectivity.unregisterNetworkCallback(callback)
            callback.working.clear()
            callback.registered = false
        }
        Dhcp6cManager.stopDaemonSync()
        running = false
        super.onDestroy()
    }
}
