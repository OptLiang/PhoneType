package com.phonetype

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class LanTcpTransport(
    private val host: String,
    private val port: Int
) : TransportClient {
    override val modeName: String = ConnectionMode.LAN.displayName

    private val ioMutex = Mutex()
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    override suspend fun connect(): ConnectionResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            connectLocked()
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                closeLocked()
            }
        }
    }

    override suspend fun send(payload: JSONObject): SendResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            if (!isConnectedLocked()) {
                val connectResult = connectLocked()
                if (!connectResult.ok) {
                    return@withLock SendResult(false, connectResult.message, connectResult.latencyMs)
                }
            }

            val firstAttempt = runCatching { sendOnceLocked(payload) }
            if (firstAttempt.isSuccess) {
                return@withLock firstAttempt.getOrThrow()
            }

            val firstError = firstAttempt.exceptionOrNull()
            closeLocked()

            val reconnectResult = connectLocked()
            if (!reconnectResult.ok) {
                return@withLock SendResult(
                    ok = false,
                    message = firstError?.message ?: reconnectResult.message
                )
            }

            runCatching { sendOnceLocked(payload) }.getOrElse { error ->
                closeLocked()
                SendResult(false, error.message ?: error::class.java.simpleName)
            }
        }
    }

    override fun isConnected(): Boolean {
        return isConnectedLocked()
    }

    private fun connectLocked(): ConnectionResult {
        closeLocked()
        val startedAt = System.nanoTime()

        return try {
            val newSocket = Socket()
            newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            newSocket.soTimeout = READ_TIMEOUT_MS

            socket = newSocket
            reader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8))

            ConnectionResult(true, "已连接", elapsedMs(startedAt))
        } catch (error: Exception) {
            closeLocked()
            ConnectionResult(false, error.message ?: error::class.java.simpleName, elapsedMs(startedAt))
        }
    }

    private fun sendOnceLocked(payload: JSONObject): SendResult {
        val currentWriter = writer ?: error("连接未建立")
        val currentReader = reader ?: error("连接未建立")
        val startedAt = System.nanoTime()

        currentWriter.write(payload.toString())
        currentWriter.write("\n")
        currentWriter.flush()

        val responseLine = currentReader.readLine() ?: error("服务端已断开")
        val response = JSONObject(responseLine)
        val message = response.optString("message", "未知错误")
        val ok = response.optBoolean("ok", false)

        return SendResult(ok, message, elapsedMs(startedAt))
    }

    private fun isConnectedLocked(): Boolean {
        val currentSocket = socket ?: return false
        return currentSocket.isConnected &&
            !currentSocket.isClosed &&
            !currentSocket.isInputShutdown &&
            !currentSocket.isOutputShutdown
    }

    private fun closeLocked() {
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        reader = null
        writer = null
        socket = null
    }

    private fun elapsedMs(startedAt: Long): Long {
        return ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3000
        const val READ_TIMEOUT_MS = 3000
    }
}
