package com.phonetype

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class ConnectionManager {
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    var connectionMode: ConnectionMode = ConnectionMode.LAN
        private set

    var lastLatencyMs: Long? = null
        private set

    private var configuredHost: String? = null
    private var configuredPort: Int? = null
    private var configuredMode: ConnectionMode? = null
    private var transportClient: TransportClient? = null
    private val operationMutex = Mutex()

    fun stateText(): String = connectionState.displayText

    fun modeName(): String = connectionMode.displayName

    fun latencyText(): String = lastLatencyMs?.let { " · ${it} ms" } ?: ""

    fun isConnected(): Boolean = transportClient?.isConnected() == true

    suspend fun connect(
        host: String,
        port: Int,
        mode: ConnectionMode = ConnectionMode.LAN
    ): ConnectionResult = operationMutex.withLock {
        connectionMode = mode
        connectionState = ConnectionState.CONNECTING
        val client = getTransport(host, port, mode)
        val result = client.connect()
        updateConnectionResult(result)
        result
    }

    suspend fun disconnect() = operationMutex.withLock {
        transportClient?.disconnect()
        lastLatencyMs = null
        connectionState = ConnectionState.DISCONNECTED
    }

    suspend fun ping(
        host: String,
        port: Int,
        mode: ConnectionMode = ConnectionMode.LAN
    ): SendResult {
        return send(host, port, JSONObject().put("type", "ping"), mode)
    }

    suspend fun sendText(
        host: String,
        port: Int,
        text: String,
        enter: Boolean,
        mode: ConnectionMode = ConnectionMode.LAN
    ): SendResult {
        val payload = JSONObject()
            .put("type", "text")
            .put("text", text)
            .put("enter", enter)

        return send(host, port, payload, mode)
    }

    suspend fun sendKey(
        host: String,
        port: Int,
        key: String,
        mode: ConnectionMode = ConnectionMode.LAN
    ): SendResult {
        val payload = JSONObject()
            .put("type", "key")
            .put("key", key)

        return send(host, port, payload, mode)
    }

    suspend fun sendShortcut(
        host: String,
        port: Int,
        keys: List<String>,
        mode: ConnectionMode = ConnectionMode.LAN
    ): SendResult {
        val payload = JSONObject()
            .put("type", "shortcut")
            .put("keys", JSONArray(keys))

        return send(host, port, payload, mode)
    }

    private suspend fun send(
        host: String,
        port: Int,
        payload: JSONObject,
        mode: ConnectionMode
    ): SendResult = operationMutex.withLock {
        connectionMode = mode
        val client = getTransport(host, port, mode)
        if (!client.isConnected()) {
            connectionState = if (connectionState == ConnectionState.CONNECTED) {
                ConnectionState.RECONNECTING
            } else {
                ConnectionState.CONNECTING
            }
        }

        val result = client.send(payload)
        updateSendResult(result)
        result
    }

    private suspend fun getTransport(
        host: String,
        port: Int,
        mode: ConnectionMode
    ): TransportClient {
        val existingClient = transportClient
        if (
            existingClient != null &&
            configuredHost == host &&
            configuredPort == port &&
            configuredMode == mode
        ) {
            return existingClient
        }

        existingClient?.disconnect()
        configuredHost = host
        configuredPort = port
        configuredMode = mode
        lastLatencyMs = null
        connectionState = ConnectionState.DISCONNECTED
        transportClient = when (mode) {
            ConnectionMode.LAN -> LanTcpTransport(host, port)
            ConnectionMode.USB_ADB -> UsbAdbTransport(port)
        }
        return transportClient ?: error("transport not configured")
    }

    private fun updateConnectionResult(result: ConnectionResult) {
        if (result.ok) {
            lastLatencyMs = result.latencyMs
            connectionState = ConnectionState.CONNECTED
        } else {
            lastLatencyMs = null
            connectionState = ConnectionState.FAILED
        }
    }

    private fun updateSendResult(result: SendResult) {
        if (result.ok) {
            lastLatencyMs = result.latencyMs
            connectionState = ConnectionState.CONNECTED
        } else {
            lastLatencyMs = null
            connectionState = ConnectionState.FAILED
        }
    }
}
