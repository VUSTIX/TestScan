package com.example.server

import android.content.Context
import com.example.server.data.AppDatabase
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WebSocketServer : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private var port: Int = 0
    private lateinit var onServerEvent: (String) -> Unit
    private lateinit var applicationContext: Context

    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val fileDao by lazy { db.fileDao() }

    private var session: DefaultWebSocketServerSession? = null
    private var server: NettyApplicationEngine? = null
    private var scanningJob: Job? = null

    fun initialize(
        port: Int,
        onServerEvent: (String) -> Unit,
        context: Context
    ) {
        this.port = port
        this.onServerEvent = onServerEvent
        this.applicationContext = context.applicationContext
        FileHandler.initialize(applicationContext, fileDao)
    }

    fun start() = launch {
        try {
            server = embeddedServer(Netty, port = port) {
                install(WebSockets) {
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                routing {
                    webSocket("/ws") {
                        session = this
                        launch {
                            while (true) {
                                val memoryInfo = FileHandler.getMemoryInfo()
                                val memoryUsage = "MEMORY:${memoryInfo["M1"]}/${memoryInfo["Mmax"]}"
                                send(Frame.Text(memoryUsage))
                                delay(100)
                            }
                        }
                        launch {
                            while (true) {
                                val txtFilePaths = withContext(Dispatchers.IO) {
                                    fileDao.getAllTxtFilePaths()
                                }
                                send(Frame.Text("TXT_FILE_PATHS:${txtFilePaths.joinToString()}"))
                                delay(5000)
                            }
                        }
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                when {
                                    text == "START_SCANNING" -> {
                                        if (scanningJob?.isActive != true) {
                                            scanningJob = launch { FileHandler.scanFileSystem() }
                                        }
                                        send(Frame.Text("SCANNING:TRUE"))
                                    }
                                    text == "STOP_SCANNING" -> {
                                        scanningJob?.cancel()
                                        send(Frame.Text("SCANNING:FALSE"))
                                    }
                                    text.startsWith("RESTORE_ITEM:") -> {
                                        val id = text.replace(Regex("[^0-9]"), "")
                                        FileHandler.replaceDirectoryWithArchive(applicationContext, id)
                                    }
                                }
                            }
                        }
                    }
                }
            }.start(wait = false)
            onServerEvent("Сервер запущен")
        } catch (e: Exception) {
            onServerEvent("Ошибка подключения к серверу: ${e.message}")
        }
    }

    fun stop() = launch {
        server?.stop(1000, 10000)
    }

    fun send(message: String) = launch {
        session?.send(Frame.Text(message))
    }

}