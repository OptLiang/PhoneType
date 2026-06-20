package com.phonetype

import org.json.JSONObject

interface TransportClient {
    val modeName: String

    suspend fun connect(): ConnectionResult

    suspend fun disconnect()

    suspend fun send(payload: JSONObject): SendResult

    fun isConnected(): Boolean
}
