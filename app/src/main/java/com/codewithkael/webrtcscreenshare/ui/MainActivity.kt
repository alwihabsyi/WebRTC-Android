package com.codewithkael.webrtcscreenshare.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.codewithkael.webrtcscreenshare.databinding.ActivityMainBinding
import com.codewithkael.webrtcscreenshare.receiver.WiFiDirectReceiver
import com.codewithkael.webrtcscreenshare.repository.MainRepository
import com.codewithkael.webrtcscreenshare.service.WebrtcService
import com.codewithkael.webrtcscreenshare.service.WebrtcServiceRepository
import dagger.hilt.android.AndroidEntryPoint
import org.webrtc.MediaStream
import javax.inject.Inject

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainRepository.Listener {

    private var username:String?=null
    private lateinit var views:ActivityMainBinding

    @Inject lateinit var webrtcServiceRepository: WebrtcServiceRepository
    private val capturePermissionRequestCode = 1

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private val peersList = mutableListOf<WifiP2pDevice>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    var isFormed: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views= ActivityMainBinding.inflate(layoutInflater)
        setContentView(views.root)

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
        initPeerDiscovery()

        init()
    }

    private fun initPeerDiscovery() {
        receiver = WiFiDirectReceiver(wifiP2pManager, channel, this)
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        registerReceiver(receiver, intentFilter)

        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                showToast("Peer discovery started")
            }

            override fun onFailure(reasonCode: Int) {
                showToast("Peer discovery failed: $reasonCode")
            }
        })
    }

    fun updatePeersList(peers: Collection<WifiP2pDevice>) {
        peersList.clear()
        peersList.addAll(peers)

        spinnerAdapter.clear()
        spinnerAdapter.addAll(peers.map { it.deviceName }.toList())
        spinnerAdapter.notifyDataSetChanged()

        for (device in peersList) {
            showToast("Device found: ${device.deviceName} - ${device.deviceAddress}")
        }
    }

    private fun connectToDevice(deviceName: String) {
        val device = peersList.find { it.deviceName == deviceName } ?: return

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                showToast("Connecting to ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                showToast("Connection failed: $reason")
            }
        })
    }

    private fun init(){
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        views.targetSpinner.adapter = spinnerAdapter

        username = intent.getStringExtra("username")
        if (username.isNullOrEmpty()){
            finish()
        }
        views.requestBtn.setOnClickListener {
            val selectedItem = views.targetSpinner.selectedItem as? String
            if (selectedItem?.isNotEmpty() == true) connectToDevice(selectedItem)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != capturePermissionRequestCode) return
        val selectedItem = views.targetSpinner.selectedItem as? String

        WebrtcService.screenPermissionIntent = data
        if (selectedItem?.isNotEmpty() == true) webrtcServiceRepository.requestConnection(selectedItem)
    }

    private fun startScreenCapture(){
        val mediaProjectionManager = application.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(), capturePermissionRequestCode
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onConnectionRequestReceived(target: String) {
        runOnUiThread{
            views.apply {
                notificationTitle.text = "$target is requesting for connection"
                notificationLayout.isVisible = true
                notificationAcceptBtn.setOnClickListener {
                    webrtcServiceRepository.acceptCAll(target)
                    notificationLayout.isVisible = false
                }
                notificationDeclineBtn.setOnClickListener {
                    notificationLayout.isVisible = false
                }
            }
        }
    }

    override fun onConnectionConnected() {
        runOnUiThread {
            views.apply {
                requestLayout.isVisible = false
                disconnectBtn.isVisible = true
                disconnectBtn.setOnClickListener {
                    webrtcServiceRepository.endCallIntent()
                    restartUi()
                }
            }
        }
    }

    override fun onCallEndReceived() {
        runOnUiThread {
            restartUi()
        }
    }

    override fun onRemoteStreamAdded(stream: MediaStream) {
        runOnUiThread {
            views.surfaceView.isVisible = true
            stream.videoTracks[0].addSink(views.surfaceView)
        }
    }

    private fun restartUi(){
        views.apply {
            disconnectBtn.isVisible=false
            requestLayout.isVisible = true
            notificationLayout.isVisible = false
            surfaceView.isVisible = false
        }
    }

    fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info?.groupFormed == true && info.groupFormed != isFormed) {
            isFormed = info.groupFormed

            username?.let { username ->
                if (info.isGroupOwner) startScreenCapture()

                WebrtcService.surfaceView = views.surfaceView
                WebrtcService.listener = this
                webrtcServiceRepository.startIntent(username, info)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}