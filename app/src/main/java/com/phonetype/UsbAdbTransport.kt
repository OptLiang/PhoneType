package com.phonetype

import org.json.JSONObject

class UsbAdbTransport(
    private val port: Int
) : TransportClient {
    private val delegate = LanTcpTransport(USB_ADB_HOST, port)

    override val modeName: String = ConnectionMode.USB_ADB.displayName

    override suspend fun connect(): ConnectionResult {
        return delegate.connect()
    }

    override suspend fun disconnect() {
        delegate.disconnect()
    }

    override suspend fun send(payload: JSONObject): SendResult {
        return delegate.send(payload)
    }

    override fun isConnected(): Boolean {
        return delegate.isConnected()
    }

    private companion object {
        const val USB_ADB_HOST = "127.0.0.1"
    }
}
