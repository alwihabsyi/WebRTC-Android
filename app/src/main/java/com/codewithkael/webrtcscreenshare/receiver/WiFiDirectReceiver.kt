@file:Suppress("DEPRECATION")

package com.codewithkael.webrtcscreenshare.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.codewithkael.webrtcscreenshare.ui.MainActivity

class WiFiDirectReceiver(
    private val wifiP2pManager: WifiP2pManager? = null,
    private val channel: WifiP2pManager.Channel? = null,
    private val activity: MainActivity? = null,
): BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d("WiFiDirect", "Wi-Fi Direct is enabled")
                } else {
                    Log.d("WiFiDirect", "Wi-Fi Direct is not enabled")
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                wifiP2pManager?.requestPeers(channel) { peers ->
                    activity?.updatePeersList(peers.deviceList)
                }
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                Log.d("WiFiDirect", "This device's connection state changed")
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (networkInfo?.isConnected == true) {
                    wifiP2pManager?.requestConnectionInfo(channel) { info ->
                        activity?.onConnectionInfoAvailable(info)
                    }
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                Log.d("WiFiDirect", "This device's Wi-Fi state changed")
            }
        }
    }

    companion object {
        fun getIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            }
        }
    }
}