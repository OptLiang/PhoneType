package com.phonetype

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

data class SendResult(
    val ok: Boolean,
    val message: String
)

object TcpClient {
    suspend fun sendText(
        host: String,
        port: Int,
        text: String,
        enter: Boolean
    ): SendResult {
        val request = JSONObject()
            .put("type", "text")
            .put("text", text)
            .put("enter", enter)

        return sendJson(host, port, request)
    }

    suspend fun ping(
        host: String,
        port: Int
    ): SendResult {
        val request = JSONObject()
            .put("type", "ping")

        return sendJson(host, port, request)
    }

    suspend fun sendKey(
        host: String,
        port: Int,
        key: String
    ): SendResult {
        val request = JSONObject()
            .put("type", "key")
            .put("key", key)

        return sendJson(host, port, request)
    }

    suspend fun sendShortcut(
        host: String,
        port: Int,
        keys: List<String>
    ): SendResult {
        val request = JSONObject()
            .put("type", "shortcut")
            .put("keys", JSONArray(keys))

        return sendJson(host, port, request)
    }

    private suspend fun sendJson(
        host: String,
        port: Int,
        request: JSONObject
    ): SendResult = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                socket.soTimeout = 3000

                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                try {
                    writer.write(request.toString())
                    writer.write("\n")
                    writer.flush()

                    val responseLine = reader.readLine()
                        ?: return@withContext SendResult(false, "服务端未返回响应")
                    val response = JSONObject(responseLine)
                    val message = response.optString("message", "未知错误")
                    val ok = response.optBoolean("ok", false)

                    SendResult(ok, message)
                } finally {
                    runCatching { reader.close() }
                    runCatching { writer.close() }
                }
            }
        } catch (error: Exception) {
            SendResult(false, error.message ?: error::class.java.simpleName)
        }
    }
}
