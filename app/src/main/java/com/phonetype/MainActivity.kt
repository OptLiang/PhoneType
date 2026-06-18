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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    val savedPort = remember(preferences) { preferences.getString("port", "8765") ?: "8765" }

    var host by remember { mutableStateOf(savedHost) }
    var portText by remember { mutableStateOf(savedPort) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("未发送") }
    var connectionStatusText by remember { mutableStateOf("未测试") }
    var isBusy by remember { mutableStateOf(false) }
    var isEditingConnection by remember { mutableStateOf(savedHost.isEmpty()) }
    var showComputerControls by remember { mutableStateOf(false) }
    var isArrowPadMode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    fun inputText() {
        if (isBusy) return

        val currentText = text
        val currentHost = host.trim()
        val port = portText.trim().toIntOrNull()

        when {
            currentText.isEmpty() -> status = "文本为空，未输入"
            currentHost.isEmpty() -> status = "请输入电脑 IP"
            port == null -> status = "端口格式错误"
            else -> {
                status = "正在输入……"
                isBusy = true
                scope.launch {
                    try {
                        val result = TcpClient.sendText(
                            host = currentHost,
                            port = port,
                            text = currentText,
                            enter = false
                        )
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

        val currentHost = host.trim()
        val port = portText.trim().toIntOrNull()

        when {
            currentHost.isEmpty() -> status = "请输入电脑 IP"
            port == null -> status = "端口格式错误"
            else -> {
                status = "正在发送……"
                isBusy = true
                scope.launch {
                    try {
                        val result = TcpClient.sendKey(currentHost, port, "enter")
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
        }
    }

    fun testConnection() {
        if (isBusy) return

        val currentHost = host.trim()
        val port = portText.trim().toIntOrNull()

        when {
            currentHost.isEmpty() -> status = "请输入电脑 IP"
            port == null -> status = "端口格式错误"
            else -> {
                status = "正在测试连接……"
                isBusy = true
                scope.launch {
                    try {
                        val result = TcpClient.ping(currentHost, port)
                        if (result.ok) {
                            status = "电脑端连接正常"
                            connectionStatusText = "连接正常"
                            isEditingConnection = false
                        } else {
                            status = "连接失败：${result.message}"
                            connectionStatusText = "连接失败"
                        }
                    } finally {
                        isBusy = false
                    }
                }
            }
        }
    }

    fun runComputerControl(
        successStatus: String,
        action: suspend (String, Int) -> SendResult
    ) {
        if (isBusy) return

        val currentHost = host.trim()
        val port = portText.trim().toIntOrNull()

        when {
            currentHost.isEmpty() -> status = "请输入电脑 IP"
            port == null -> status = "端口格式错误"
            else -> {
                status = "正在执行……"
                isBusy = true
                scope.launch {
                    try {
                        val result = action(currentHost, port)
                        status = if (result.ok) {
                            successStatus
                        } else {
                            "操作失败：${result.message}"
                        }
                    } finally {
                        isBusy = false
                    }
                }
            }
        }
    }

    fun updateHost(value: String) {
        host = value
        connectionStatusText = "未测试"
    }

    fun updatePort(value: String) {
        portText = value
        connectionStatusText = "未测试"
    }

    fun collapseConnectionEditor() {
        if (host.trim().isEmpty()) {
            status = "请输入电脑 IP"
        } else {
            isEditingConnection = false
        }
    }

    fun toggleArrowPadMode() {
        if (isArrowPadMode) {
            isArrowPadMode = false
            status = "已退出方向模式"
        } else {
            isArrowPadMode = true
            showComputerControls = false
            status = "方向模式"
        }
    }

    LaunchedEffect(host, portText) {
        preferences.edit()
            .putString("host", host)
            .putString("port", portText)
            .apply()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()

        val currentHost = host.trim()
        val port = portText.trim().toIntOrNull()
        when {
            currentHost.isEmpty() -> {
                connectionStatusText = "未设置"
                isEditingConnection = true
            }
            port == null -> {
                connectionStatusText = "连接失败"
                isEditingConnection = true
            }
            else -> {
                connectionStatusText = "正在连接"
                isEditingConnection = false
                val result = TcpClient.ping(currentHost, port)
                if (result.ok) {
                    connectionStatusText = "连接正常"
                    isEditingConnection = false
                } else {
                    connectionStatusText = "连接失败"
                    isEditingConnection = true
                }
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

        val showConnectionEditor = isEditingConnection
        if (showConnectionEditor) {
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
                    onClick = { collapseConnectionEditor() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("收起")
                }
            }
        } else {
            val displayHost = host.trim()
            val displayPort = portText.trim().ifEmpty { "8765" }
            val connectionText = if (displayHost.isEmpty()) {
                "未设置电脑地址"
            } else {
                "目标电脑：$displayHost:$displayPort · $connectionStatusText"
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = connectionText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        keyboardController?.hide()
                        isEditingConnection = true
                    }
                ) {
                    Text("修改")
                }
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("输入文本") },
            minLines = if (isKeyboardVisible) 6 else 8,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusRequester(focusRequester)
        )

        if (!isKeyboardVisible && !isArrowPadMode) {
            Button(
                onClick = { showComputerControls = !showComputerControls },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showComputerControls) "收起电脑控制" else "电脑控制")
            }
        }

        if (showComputerControls && !isKeyboardVisible && !isArrowPadMode) {
            ComputerControlRows(
                isBusy = isBusy,
                runComputerControl = { successStatus, action ->
                    runComputerControl(successStatus, action)
                }
            )
        }

        if (isArrowPadMode) {
            ArrowPad(
                isBusy = isBusy,
                runComputerControl = { successStatus, action ->
                    runComputerControl(successStatus, action)
                },
                onExit = {
                    isArrowPadMode = false
                    status = "已退出方向模式"
                }
            )
        }

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
private fun ArrowPad(
    isBusy: Boolean,
    runComputerControl: (String, suspend (String, Int) -> SendResult) -> Unit,
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
                onClick = {
                    runComputerControl("上移") { currentHost, currentPort ->
                        TcpClient.sendKey(currentHost, currentPort, "up")
                    }
                },
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
                onClick = {
                    runComputerControl("左移") { currentHost, currentPort ->
                        TcpClient.sendKey(currentHost, currentPort, "left")
                    }
                },
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
                onClick = {
                    runComputerControl("右移") { currentHost, currentPort ->
                        TcpClient.sendKey(currentHost, currentPort, "right")
                    }
                },
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
                onClick = {
                    runComputerControl("下移") { currentHost, currentPort ->
                        TcpClient.sendKey(currentHost, currentPort, "down")
                    }
                },
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
    runComputerControl: (String, suspend (String, Int) -> SendResult) -> Unit
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
            onClick = {
                runComputerControl("已退格") { currentHost, currentPort ->
                    TcpClient.sendKey(currentHost, currentPort, "backspace")
                }
            },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("退格")
        }
        Button(
            onClick = {
                runComputerControl("已删除") { currentHost, currentPort ->
                    TcpClient.sendKey(currentHost, currentPort, "delete")
                }
            },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("Delete")
        }
        Button(
            onClick = {
                runComputerControl("已换行") { currentHost, currentPort ->
                    TcpClient.sendShortcut(currentHost, currentPort, listOf("shift", "enter"))
                }
            },
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
            onClick = {
                runComputerControl("已全选") { currentHost, currentPort ->
                    TcpClient.sendShortcut(currentHost, currentPort, listOf("ctrl", "a"))
                }
            },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("全选")
        }
        Button(
            onClick = {
                runComputerControl("已复制") { currentHost, currentPort ->
                    TcpClient.sendShortcut(currentHost, currentPort, listOf("ctrl", "c"))
                }
            },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("复制")
        }
        Button(
            onClick = {
                runComputerControl("已剪切") { currentHost, currentPort ->
                    TcpClient.sendShortcut(currentHost, currentPort, listOf("ctrl", "x"))
                }
            },
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
            onClick = {
                runComputerControl("已粘贴") { currentHost, currentPort ->
                    TcpClient.sendShortcut(currentHost, currentPort, listOf("ctrl", "v"))
                }
            },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("粘贴")
        }
        Button(
            onClick = {
                runComputerControl("已撤销") { currentHost, currentPort ->
                    TcpClient.sendShortcut(currentHost, currentPort, listOf("ctrl", "z"))
                }
            },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("撤销")
        }
        Button(
            onClick = {
                runComputerControl("已触发截图") { currentHost, currentPort ->
                    TcpClient.sendShortcut(currentHost, currentPort, listOf("win", "shift", "s"))
                }
            },
            enabled = !isBusy,
            colors = controlButtonColors,
            modifier = Modifier.weight(1f)
        ) {
            Text("截图")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhoneTypeAppPreview() {
    PhoneTypeApp()
}
