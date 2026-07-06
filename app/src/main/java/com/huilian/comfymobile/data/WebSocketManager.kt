package com.huilian.comfymobile.data

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WebSocketManager {
    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 20_000
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private var job: Job? = null

    private val _events = MutableSharedFlow<WebSocketEvent>()
    val events = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<Boolean>()
    val connectionState = _connectionState.asSharedFlow()

    suspend fun connect(url: String) {
        disconnect()
        try {
            client.webSocket(urlString = url) {
                session = this
                _connectionState.emit(true)
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            parseEvent(text)?.let { _events.emit(it) }
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            _connectionState.emit(false)
        }
    }

    suspend fun disconnect() {
        session?.close()
        session = null
        _connectionState.emit(false)
    }

    fun isConnected(): Boolean = session != null

    private fun parseEvent(jsonText: String): WebSocketEvent? {
        return try {
            val json = Json.parseToJsonElement(jsonText).jsonObject
            val type = json["type"]?.jsonPrimitive?.content ?: return null
            val data = json["data"]
            WebSocketEvent(type, data)
        } catch (e: Exception) {
            null
        }
    }
}

data class WebSocketEvent(
    val type: String,
    val data: kotlinx.serialization.json.JsonElement?
)
