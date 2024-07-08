package com.example.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

object WebSocketClient : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private var ip: String? = null
    private var port: Int? = null
    private var onError: ((String) -> Unit)? = null
    private var session: DefaultClientWebSocketSession? = null
    private val client = HttpClient {
        install(WebSockets)
    }

    fun initialize(
        ip: String,
        port: Int,
        onError: (String) -> Unit
    ) {
        this.ip = ip
        this.port = port
        this.onError = onError
    }

    fun connect(
        onMemoryUpdate: (String) -> Unit,
        onScanUpdate: (String) -> Unit,
        onScanListUpdate: (String) -> Unit,
        onScanningUpdate: (String) -> Unit,
        onConnectUpdate: (String) -> Unit,
        onArchiveUpgrade: (String) -> Unit
    ) = launch {
        try {
            client.webSocket(host = ip!!, port = port!!, path = "/ws") {
                session = this
                send(Frame.Text("START_SCANNING"))
                onConnectUpdate("TRUE")
                incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Text -> {
                            val message = frame.readText()
                            if (message.startsWith("MEMORY:")) {
                                onMemoryUpdate(message.removePrefix("MEMORY:"))
                            } else if (message.startsWith("SCAN:")) {
                                onScanUpdate(message.removePrefix("SCAN:"))
                            } else if (message.startsWith("TXT_FILE_PATHS:")) {
                                onScanListUpdate(message.removePrefix("TXT_FILE_PATHS:"))
                            } else if (message.startsWith("SCANNING:")) {
                                onScanningUpdate(message.removePrefix("SCANNING:"))
                            } else if (message.startsWith("CONNECT:")) {
                                onConnectUpdate(message.removePrefix("CONNECT:"))
                            } else if (message.startsWith("REPLACED_ARCHIVE:")) {
                                onArchiveUpgrade(message.removePrefix("REPLACED_ARCHIVE:"))
                            }
                        }
                        is Frame.Ping -> println("Received ping")
                        is Frame.Close -> println("Connection closed: ${frame.readReason()}")
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            onError?.invoke("Ошибка подключения к серверу")
        }
    }

    fun send(message: String) = launch {
        session?.send(Frame.Text(message))
    }

}