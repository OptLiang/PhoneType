package com.phonetype

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneTypeApp() }
    }
}

@Composable
fun PhoneTypeApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            PhoneTypeScreen()
        }
    }
}

@Composable
private fun PhoneTypeScreen() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("phone_type_settings", Context.MODE_PRIVATE)
    }
    val savedHost = remember(preferences) { preferences.getString("host", "") ?: "" }
    val savedPort = remember(preferences) { preferences.getString("port", DEFAULT_PORT_TEXT) ?: DEFAULT_PORT_TEXT }
    val savedMode = remember(preferences) {
        ConnectionMode.fromStored(preferences.getString("mode", ConnectionMode.LAN.name))
    }
    val connectionManager = remember { ConnectionManager() }

    var host by remember { mutableStateOf(savedHost) }
    var portText by remember { mutableStateOf(savedPort) }
    var connectionMode by remember { mutableStateOf(savedMode) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("未发送") }
    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var latencyText by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var isEditingConnection by remember {
        mutableStateOf(connectionMode == ConnectionMode.LAN && savedHost.isEmpty())
    }
    var showComputerControls by remember { mutableStateOf(false) }
    var isArrowPadMode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val connectionEditorScrollState = rememberScrollState()
    val connectionEditorMaxHeight = if (isKeyboardVisible) 180.dp else 280.dp
    val textMinLines = when {
        isKeyboardVisible -> 4
        isEditingConnection -> 5
        else -> 8
    }

    fun refreshConnectionState() {
        connectionState = connectionManager.connectionState
        latencyText = if (connectionState == ConnectionState.CONNECTED) {
            connectionManager.latencyText()
        } else {
            ""
        }
    }

    fun disconnectCurrentTransport(nextStatus: String) {
        connectionState = ConnectionState.DISCONNECTED
        status = nextStatus
        scope.launch {
            connectionManager.disconnect()
            refreshConnectionState()
        }
    }

    fun lanEndpointOrNull(): ConnectionEndpoint? {
        val currentHost = host.trim()
        val port = portText.trim().toIntOrNull()
        return if (currentHost.isNotEmpty() && port != null) {
            ConnectionEndpoint(currentHost, port)
        } else {
            null
        }
    }

    fun endpointForMode(mode: ConnectionMode): ConnectionEndpoint? {
        return when (mode) {
            ConnectionMode.LAN -> lanEndpointOrNull()
            ConnectionMode.USB_ADB -> ConnectionEndpoint(USB_ADB_HOST, DEFAULT_PORT)
        }
    }

    fun currentEndpointOrNull(): ConnectionEndpoint? {
        if (connectionMode == ConnectionMode.USB_ADB) {
            return endpointForMode(ConnectionMode.USB_ADB)
        }

        val currentHost = host.trim()
        val port = portText.trim().toIntOrNull()
        return when {
            currentHost.isEmpty() -> {
                status = "请输入电脑 IP"
                null
            }
            port == null -> {
                status = "端口格式错误"
                null
            }
            else -> ConnectionEndpoint(currentHost, port)
        }
    }

    fun prepareSendState() {
        connectionState = if (connectionManager.isConnected()) {
            ConnectionState.CONNECTED
        } else if (connectionState == ConnectionState.DISCONNECTED) {
            ConnectionState.CONNECTING
        } else {
            ConnectionState.RECONNECTING
        }
    }

    fun connectToComputer() {
        if (isBusy) return

        val endpoint = currentEndpointOrNull() ?: return
        status = "正在连接……"
        connectionState = ConnectionState.CONNECTING
        isBusy = true
        scope.launch {
            try {
                val result = connectionManager.connect(endpoint.host, endpoint.port, connectionMode)
                refreshConnectionState()
                status = if (result.ok) {
                    isEditingConnection = false
                    "连接成功"
                } else {
                    "连接失败：${result.message}"
                }
            } finally {
                isBusy = false
            }
        }
    }

    fun testConnection() {
        if (isBusy) return

        val endpoint = currentEndpointOrNull() ?: return
        status = "正在测试连接……"
        connectionState = ConnectionState.CONNECTING
        isBusy = true
        scope.launch {
            try {
                val result = connectionManager.ping(endpoint.host, endpoint.port, connectionMode)
                refreshConnectionState()
                status = if (result.ok) {
                    "电脑端连接正常"
                } else {
                    "连接失败：${result.message}"
                }
            } finally {
                isBusy = false
            }
        }
    }

    fun disconnectFromComputer() {
        if (isBusy) return

        status = "正在断开……"
        isBusy = true
        scope.launch {
            try {
                connectionManager.disconnect()
                refreshConnectionState()
                status = "已断开"
            } finally {
                isBusy = false
            }
        }
    }

    fun inputText() {
        if (isBusy) return

        val currentText = text
        val endpoint = currentEndpointOrNull()
        when {
            currentText.isEmpty() -> status = "文本为空，未输入"
            endpoint == null -> Unit
            else -> {
                status = "正在输入……"
                prepareSendState()
                isBusy = true
                scope.launch {
                    try {
                        val result = connectionManager.sendText(
                            host = endpoint.host,
                            port = endpoint.port,
                            text = currentText,
                            enter = false,
                            mode = connectionMode
                        )
                        refreshConnectionState()
                        status = if (result.ok) {
                            text = ""
                            "输入成功"
                        } else {
                            "输入失败：${result.message}"
                        }
                    } finally {
                        isBusy = false
                    }
                }
            }
        }
    }

    fun sendEnter() {
        if (isBusy) return

        val endpoint = currentEndpointOrNull() ?: return
        status = "正在发送……"
        prepareSendState()
        isBusy = true
        scope.launch {
            try {
                val result = connectionManager.sendKey(endpoint.host, endpoint.port, "enter", connectionMode)
                refreshConnectionState()
                status = if (result.ok) {
                    "已发送"
                } else {
                    "发送失败：${result.message}"
                }
            } finally {
                isBusy = false
            }
        }
    }

    fun runComputerControl(
        successStatus: String,
        action: suspend (ConnectionEndpoint) -> SendResult
    ) {
        if (isBusy) return

        val endpoint = currentEndpointOrNull() ?: return
        status = "正在执行……"
        prepareSendState()
        scope.launch {
            val result = action(endpoint)
            refreshConnectionState()
            status = if (result.ok) {
                successStatus
            } else {
                "操作失败：${result.message}"
            }
        }
    }

    fun updateHost(value: String) {
        host = value
        disconnectCurrentTransport("连接配置已修改")
    }

    fun updatePort(value: String) {
        portText = value
        disconnectCurrentTransport("连接配置已修改")
    }

    fun updateConnectionMode(mode: ConnectionMode) {
        if (connectionMode == mode) return

        connectionMode = mode
        isEditingConnection = true
        disconnectCurrentTransport("已切换到${mode.displayName}模式")
    }

    fun collapseConnectionEditor() {
        if (connectionMode == ConnectionMode.LAN && host.trim().isEmpty()) {
            status = "请输入电脑 IP"
        } else {
            isEditingConnection = false
        }
    }

    fun toggleConnectionEditor() {
        keyboardController?.hide()
        focusManager.clearFocus()
        if (isEditingConnection) {
            collapseConnectionEditor()
        } else {
            isEditingConnection = true
        }
    }

    fun toggleComputerControls() {
        if (isKeyboardVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
            showComputerControls = true
            isArrowPadMode = false
        } else {
            showComputerControls = !showComputerControls
            isArrowPadMode = false
        }
    }

    fun toggleArrowPadMode() {
        if (isArrowPadMode) {
            isArrowPadMode = false
            status = "已退出方向模式"
        } else {
            keyboardController?.hide()
            focusManager.clearFocus()
            isArrowPadMode = true
            showComputerControls = false
            status = "方向模式"
        }
    }

    LaunchedEffect(host, portText, connectionMode) {
        preferences.edit()
            .putString("host", host)
            .putString("port", portText)
            .putString("mode", connectionMode.name)
            .apply()
    }

    suspend fun autoConnectOnStartup() {
        val lanEndpoint = lanEndpointOrNull()
        val attempts = mutableListOf<Pair<ConnectionMode, ConnectionEndpoint>>()
        if (savedMode == ConnectionMode.USB_ADB) {
            attempts += ConnectionMode.USB_ADB to ConnectionEndpoint(USB_ADB_HOST, DEFAULT_PORT)
            if (lanEndpoint != null) {
                attempts += ConnectionMode.LAN to lanEndpoint
            }
        } else {
            if (lanEndpoint != null) {
                attempts += ConnectionMode.LAN to lanEndpoint
            }
            attempts += ConnectionMode.USB_ADB to ConnectionEndpoint(USB_ADB_HOST, DEFAULT_PORT)
        }

        val failures = mutableListOf<String>()
        isEditingConnection = false
        for ((mode, endpoint) in attempts) {
            connectionMode = mode
            connectionState = if (failures.isEmpty()) {
                ConnectionState.CONNECTING
            } else {
                ConnectionState.RECONNECTING
            }
            status = if (failures.isEmpty()) {
                "正在自动连接${mode.displayName}……"
            } else {
                "正在尝试备用${mode.displayName}……"
            }

            val result = connectionManager.connect(endpoint.host, endpoint.port, mode)
            refreshConnectionState()
            if (result.ok) {
                isEditingConnection = false
                status = "已自动连接${mode.displayName}"
                return
            }
            failures += "${mode.displayName}：${result.message}"
        }

        if (lanEndpoint == null) {
            failures += "${ConnectionMode.LAN.displayName}：未保存有效 IP/端口"
        }
        connectionState = ConnectionState.FAILED
        latencyText = ""
        isEditingConnection = true
        status = "自动连接失败：${failures.joinToString("；")}"
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()

        isBusy = true
        try {
            autoConnectOnStartup()
        } finally {
            isBusy = false
        }
    }

    DisposableEffect(connectionManager) {
        onDispose {
            CoroutineScope(Dispatchers.IO).launch {
                connectionManager.disconnect()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isKeyboardVisible) 8.dp else 12.dp)
    ) {
        Text(
            text = "PhoneType",
            style = MaterialTheme.typography.headlineMedium
        )

        val visibleLatencyText = if (connectionState == ConnectionState.CONNECTED) latencyText else ""
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "当前连接：${connectionMode.displayName} · ${connectionState.displayText}$visibleLatencyText",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { toggleConnectionEditor() }) {
                Text(if (isEditingConnection) "收起" else "修改")
            }
        }

        if (isEditingConnection) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = connectionEditorMaxHeight)
                    .verticalScroll(connectionEditorScrollState)
            ) {
                ConnectionModeSelector(
                    connectionMode = connectionMode,
                    onModeSelected = { updateConnectionMode(it) }
                )

                if (connectionMode == ConnectionMode.LAN) {
                    LanConnectionEditor(
                        host = host,
                        portText = portText,
                        isBusy = isBusy,
                        updateHost = { updateHost(it) },
                        updatePort = { updatePort(it) },
                        testConnection = { testConnection() },
                        connectToComputer = { connectToComputer() },
                        disconnectFromComputer = { disconnectFromComputer() }
                    )
                } else {
                    UsbConnectionEditor(
                        isBusy = isBusy,
                        testConnection = { testConnection() },
                        connectToComputer = { connectToComputer() },
                        disconnectFromComputer = { disconnectFromComputer() }
                    )
                }
            }
        } else {
            val connectionTarget = if (connectionMode == ConnectionMode.USB_ADB) {
                "目标地址：$USB_ADB_HOST:$DEFAULT_PORT（ADB reverse）"
            } else {
                val displayHost = host.trim()
                val displayPort = portText.trim().ifEmpty { DEFAULT_PORT_TEXT }
                if (displayHost.isEmpty()) {
                    "未设置电脑地址"
                } else {
                    "目标电脑：$displayHost:$displayPort"
                }
            }
            Text(
                text = connectionTarget,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("输入文本") },
            minLines = textMinLines,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusRequester(focusRequester)
        )

        Button(
            onClick = { toggleComputerControls() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showComputerControls && !isKeyboardVisible) "收起电脑控制" else "电脑控制")
        }

        if (showComputerControls && !isKeyboardVisible && !isArrowPadMode) {
            ComputerControlRows(
                isBusy = isBusy,
                sendKey = { successStatus, keyName ->
                    runComputerControl(successStatus) { endpoint ->
                        connectionManager.sendKey(endpoint.host, endpoint.port, keyName, connectionMode)
                    }
                },
                sendShortcut = { successStatus, keys ->
                    runComputerControl(successStatus) { endpoint ->
                        connectionManager.sendShortcut(endpoint.host, endpoint.port, keys, connectionMode)
                    }
                }
            )
        }

        if (isArrowPadMode) {
            ArrowPad(
                isBusy = isBusy,
                sendKey = { successStatus, keyName ->
                    runComputerControl(successStatus) { endpoint ->
                        connectionManager.sendKey(endpoint.host, endpoint.port, keyName, connectionMode)
                    }
                },
                onExit = {
                    isArrowPadMode = false
                    status = "已退出方向模式"
                }
            )
        }

        Text(
            text = "最近操作：$status",
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (isKeyboardVisible) 2 else 3
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { inputText() },
                enabled = !isBusy,
                modifier = Modifier.weight(1f)
            ) {
                Text("输入")
            }
            Button(
                onClick = { toggleArrowPadMode() },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("方向")
            }
            Button(
                onClick = { sendEnter() },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun ConnectionModeSelector(
    connectionMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit
) {
    val selectedColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    )
    val defaultColors = ButtonDefaults.buttonColors()

    Text(
        text = "连接方式：",
        style = MaterialTheme.typography.titleSmall
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = { onModeSelected(ConnectionMode.LAN) },
            colors = if (connectionMode == ConnectionMode.LAN) selectedColors else defaultColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("局域网")
        }
        Button(
            onClick = { onModeSelected(ConnectionMode.USB_ADB) },
            colors = if (connectionMode == ConnectionMode.USB_ADB) selectedColors else defaultColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("USB 数据线")
        }
    }
}

@Composable
private fun LanConnectionEditor(
    host: String,
    portText: String,
    isBusy: Boolean,
    updateHost: (String) -> Unit,
    updatePort: (String) -> Unit,
    testConnection: () -> Unit,
    connectToComputer: () -> Unit,
    disconnectFromComputer: () -> Unit
) {
    Text(
        text = "局域网模式：",
        style = MaterialTheme.typography.titleSmall
    )
    OutlinedTextField(
        value = host,
        onValueChange = { updateHost(it) },
        label = { Text("电脑 IP") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = portText,
        onValueChange = { updatePort(it) },
        label = { Text("端口") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )

    ConnectionActionRow(
        isBusy = isBusy,
        testConnection = testConnection,
        connectToComputer = connectToComputer,
        disconnectFromComputer = disconnectFromComputer
    )
}

@Composable
private fun UsbConnectionEditor(
    isBusy: Boolean,
    testConnection: () -> Unit,
    connectToComputer: () -> Unit,
    disconnectFromComputer: () -> Unit
) {
    Text(
        text = "USB 模式：",
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        text = "提示：请先在电脑端点击“一键配置 USB 连接”",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "端口：$DEFAULT_PORT",
        style = MaterialTheme.typography.bodyMedium
    )
    ConnectionActionRow(
        isBusy = isBusy,
        testConnection = testConnection,
        connectToComputer = connectToComputer,
        disconnectFromComputer = disconnectFromComputer
    )
}

@Composable
private fun ConnectionActionRow(
    isBusy: Boolean,
    testConnection: () -> Unit,
    connectToComputer: () -> Unit,
    disconnectFromComputer: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = { testConnection() },
            enabled = !isBusy,
            modifier = Modifier.weight(1f)
        ) {
            Text("测试连接")
        }
        Button(
            onClick = { connectToComputer() },
            enabled = !isBusy,
            modifier = Modifier.weight(1f)
        ) {
            Text("连接")
        }
        Button(
            onClick = { disconnectFromComputer() },
            enabled = !isBusy,
            modifier = Modifier.weight(1f)
        ) {
            Text("断开")
        }
    }
}

@Composable
private fun ArrowPad(
    isBusy: Boolean,
    sendKey: (String, String) -> Unit,
    onExit: () -> Unit
) {
    val arrowButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { sendKey("上移", "up") },
                enabled = !isBusy,
                colors = arrowButtonColors,
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .height(56.dp)
            ) {
                Text("↑")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { sendKey("左移", "left") },
                enabled = !isBusy,
                colors = arrowButtonColors,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("←")
            }
            Button(
                onClick = { onExit() },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("退出")
            }
            Button(
                onClick = { sendKey("右移", "right") },
                enabled = !isBusy,
                colors = arrowButtonColors,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("→")
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { sendKey("下移", "down") },
                enabled = !isBusy,
                colors = arrowButtonColors,
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .height(56.dp)
            ) {
                Text("↓")
            }
        }
    }
}

@Composable
private fun ComputerControlRows(
    isBusy: Boolean,
    sendKey: (String, String) -> Unit,
    sendShortcut: (String, List<String>) -> Unit
) {
    val controlButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    Text(
        text = "电脑控制",
        style = MaterialTheme.typography.titleMedium
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = { sendKey("已退格", "backspace") },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("退格")
        }
        Button(
            onClick = { sendKey("已删除", "delete") },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("Delete")
        }
        Button(
            onClick = { sendShortcut("已换行", listOf("shift", "enter")) },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("换行")
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = { sendShortcut("已全选", listOf("ctrl", "a")) },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("全选")
        }
        Button(
            onClick = { sendShortcut("已复制", listOf("ctrl", "c")) },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("复制")
        }
        Button(
            onClick = { sendShortcut("已剪切", listOf("ctrl", "x")) },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("剪切")
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = { sendShortcut("已粘贴", listOf("ctrl", "v")) },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("粘贴")
        }
        Button(
            onClick = { sendShortcut("已撤销", listOf("ctrl", "z")) },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("撤销")
        }
        Button(
            onClick = { sendShortcut("已触发截图", listOf("win", "shift", "s")) },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("截图")
        }
    }
}

private data class ConnectionEndpoint(
    val host: String,
    val port: Int
)

private const val DEFAULT_PORT = 8765
private const val DEFAULT_PORT_TEXT = "8765"
private const val USB_ADB_HOST = "127.0.0.1"

@Preview(showBackground = true)
@Composable
private fun PhoneTypeAppPreview() {
    PhoneTypeApp()
}
