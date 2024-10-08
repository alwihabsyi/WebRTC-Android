package com.codewithkael.webrtcscreenshare.socket

import android.util.Log
import com.codewithkael.webrtcscreenshare.utils.DataModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketClient @Inject constructor(
    private val gson: Gson
) {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    var listener: Listener? = null

    fun startServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(SOCKET_PORT)
                clientSocket = serverSocket?.accept()

                Log.d("SocketClient", "Client connected")

                listenForMessages(clientSocket)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun connectToServer(groupOwnerAddress: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                clientSocket = Socket(groupOwnerAddress, SOCKET_PORT)
                Log.d("SocketClient", "Connected to server")

                listenForMessages(clientSocket)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun listenForMessages(socket: Socket?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                var message: String?

                while (reader.readLine().also { message = it } != null) {
                    val model = try {
                        gson.fromJson(message, DataModel::class.java)
                    } catch (e: Exception) {
                        null
                    }
                    Log.d("SocketClient", "onMessage: $model")
                    model?.let { listener?.onNewMessageReceived(it) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessageToSocket(message: Any?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val writer = PrintWriter(BufferedWriter(OutputStreamWriter(clientSocket?.getOutputStream())), true)
                writer.println(gson.toJson(message))
                writer.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun onDestroy() {
        try {
            serverSocket?.close()
            clientSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    interface Listener {
        fun onNewMessageReceived(model: DataModel)
    }

    companion object {
        const val SOCKET_PORT = 8889
    }
}
