package com.phonetype

data class SendResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long? = null
)

data class ConnectionResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long? = null
)

enum class ConnectionMode(val displayName: String) {
    LAN("局域网"),
    USB_ADB("USB 数据线");

    companion object {
        fun fromStored(value: String?): ConnectionMode {
            return values().firstOrNull { it.name == value } ?: LAN
        }
    }
}

enum class ConnectionState(val displayText: String) {
    DISCONNECTED("未连接"),
    CONNECTING("连接中"),
    CONNECTED("已连接"),
    RECONNECTING("重连中"),
    FAILED("连接失败")
}
