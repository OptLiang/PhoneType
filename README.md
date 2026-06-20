# PhoneType

PhoneType 是一个“手机输入法电脑输入器”。用户在 Android 手机上输入和预览文本，应用通过 JSON Lines + TCP 协议把文本或按键请求发送到 Windows 电脑端服务，电脑端再写入剪贴板并模拟键盘输入到当前光标位置。

当前版本：v2.0 stable。

## 当前支持

PhoneType v2.0 是 LAN + USB 双通道稳定版，当前能力如下：

* LAN TCP 长连接：连接成功后复用同一 TCP socket 连续发送多条 JSON Lines 请求。
* USB ADB reverse：通过 `adb reverse tcp:8765 tcp:8765`，手机端连接 `127.0.0.1:8765`，仍复用同一 JSON Lines + TCP 协议。
* Android 手机端输入文本并预览。
* 输入中文、英文、emoji、多行文本。
* 输入成功后自动清空手机输入框。
* Enter 发送。
* Shift + Enter 换行。
* 退格、Delete、全选、复制、剪切、粘贴、撤销。
* Win + Shift + S 触发 Windows 截图。
* 上下左右方向键。
* 手机端记忆上次连接方式、局域网 IP 和端口。
* 启动自动连接：优先尝试上次连接方式，失败后尝试备用 LAN / USB。
* 最近延迟显示：连接、测试和发送成功后显示最近响应耗时。
* 手动点击“连接”只连接当前选择模式，不自动切换到另一个模式。
* 电脑端 tkinter 启动器，可启动/停止服务、显示最近客户端和最近操作。
* 电脑端启动器可一键配置 USB ADB reverse。

## 当前未支持

* 蓝牙仍未实现：不支持 RFCOMM 和 BLE GATT。
* Android 原生 USB Host / Accessory。
* USB / LAN / 蓝牙三模式完整自动切换。
* 云同步、账号系统、历史记录、剪贴板同步。

## 项目结构

```text
PhoneType
├── app
│   └── src/main
│       ├── AndroidManifest.xml
│       ├── java/com/phonetype
│       │   ├── MainActivity.kt
│       │   ├── ConnectionManager.kt
│       │   ├── ConnectionModels.kt
│       │   ├── TransportClient.kt
│       │   ├── LanTcpTransport.kt
│       │   ├── UsbAdbTransport.kt
│       │   └── TcpClient.kt
│       └── res
├── PhoneTypePC
│   ├── launcher.py
│   ├── launcher.pyw
│   ├── requirements.txt
│   └── server.py
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 技术栈

### Android 端

* Kotlin
* Jetpack Compose
* Material3
* `java.net.Socket`
* `org.json.JSONObject`

### Windows 电脑端

* Python
* tkinter
* socketserver
* pyperclip
* pynput
* adb（仅 USB ADB reverse 模式需要）

## 电脑端使用方法

进入电脑端目录：

```powershell
cd D:\AndroidStudioProjects\PhoneType\PhoneTypePC
```

安装依赖：

```powershell
pip install -r requirements.txt
```

启动图形界面监听器：

```powershell
python launcher.py
```

也可以双击：

```text
PhoneTypePC\launcher.pyw
```

启动后，窗口会显示本机局域网 IP、端口、服务状态、最近客户端和最近操作。

## LAN TCP 使用步骤

电脑端：

1. 打开 `PhoneTypePC\launcher.pyw` 或运行 `python launcher.py`。
2. 点击“启动服务”。
3. 记录窗口显示的本机地址，例如 `192.168.1.8:8765`。

手机端：

1. 打开 PhoneType。
2. 点击“修改”。
3. 选择“局域网”。
4. 输入电脑端显示的 IP 和端口。
5. 点击“连接”。
6. 顶部显示“当前连接：局域网 · 已连接 · xx ms”后即可输入。

LAN 模式连接后会复用长连接，连续方向键和快捷键不再每次新建 TCP 连接。

## 自动连接策略

App 启动后会优先尝试上次连接方式：

* 上次使用 USB 数据线时，先尝试 `127.0.0.1:8765`，失败后如果保存过有效 LAN IP 和端口，再尝试 LAN。
* 上次使用局域网时，先尝试保存的 LAN IP 和端口，失败后再尝试 USB ADB reverse。
* 两种方式都失败时，App 会展开连接配置区并显示失败原因。
* 用户手动点击“连接”时只连接当前选择模式，不自动切换到另一个模式。

## 延迟显示

Android 端会在本地统计连接、测试连接和发送请求的耗时，成功后顶部状态显示最近延迟，例如：

```text
当前连接：局域网 · 已连接 · 23 ms
当前连接：USB 数据线 · 已连接 · 16 ms
```

延迟不由电脑端返回，不修改 JSON 协议字段。

## USB ADB reverse 使用步骤

USB 模式不使用 Android 原生 USB API，也不需要新增 Android 权限。它依赖 adb reverse，把手机端的 `127.0.0.1:8765` 转发到电脑端 `127.0.0.1:8765`。

电脑端：

1. 手机用 USB 数据线连接电脑。
2. 手机打开开发者选项和 USB 调试。
3. 打开 PhoneType PC launcher。
4. 点击“一键配置 USB 连接”。
5. 如果手机弹出 USB 调试授权，点击允许。
6. 确认状态显示“USB 转发已配置，手机端请选择 USB 数据线模式并连接”。

手机端：

1. 打开 PhoneType。
2. 点击“修改”。
3. 选择“USB 数据线”。
4. 点击“连接”。
5. 顶部显示“当前连接：USB 数据线 · 已连接 · xx ms”后即可输入。

清除 USB 转发时，可在 launcher 中点击“清除 USB 转发”。

## 手机端按钮说明

### 输入

将手机输入框中的内容输入到电脑当前光标位置。输入成功后，手机输入框会自动清空。

### 发送

向电脑发送 Enter。适合微信、网页聊天框、评论框等“Enter 发送”的场景。

### 方向

打开方向控制区，用于控制电脑光标上下左右移动。再次点击退出按钮可退出方向模式。

### 电脑控制

包含常用电脑快捷键：

* 退格
* Delete
* 换行
* 全选
* 复制
* 剪切
* 粘贴
* 撤销
* 截图

输入法弹出时点击“电脑控制”，App 会先收起输入法并显示电脑控制区。

## 通信协议

默认端口：

```text
8765
```

通信格式为 JSON Lines，每条消息一行 JSON。LAN 和 USB ADB reverse 共用同一协议。

### 输入文本

```json
{"type":"text","text":"你好，PhoneType","enter":false}
```

### 测试连接

```json
{"type":"ping"}
```

### 单键控制

```json
{"type":"key","key":"backspace"}
```

### 快捷键控制

```json
{"type":"shortcut","keys":["ctrl","c"]}
```

服务端只接受内置白名单中的 key 和 shortcut，不允许客户端执行任意命令。

## Android 本机编译

```powershell
cd D:\AndroidStudioProjects\PhoneType
$env:GRADLE_USER_HOME="$PWD\.gradle-user-home"
.\gradlew.bat assembleDebug --stacktrace --no-daemon
```

## 常见问题

### 自动连接失败怎么办

1. 点“修改”展开连接区。
2. 手动选择 LAN 或 USB。
3. 手动连接只连接当前选择模式，不会自动切换到另一个模式。

### 找不到 adb

launcher 会按以下顺序查找 adb：

1. 用户手动选择的 adb.exe（点击"选择 adb.exe"按钮指定）
2. PATH 中的 adb
3. `ANDROID_HOME/platform-tools/adb.exe`
4. `ANDROID_SDK_ROOT/platform-tools/adb.exe`
5. 项目根目录 `local.properties` 中的 `sdk.dir`
6. 常见兜底路径：`D:\Android\Sdk`、`%LOCALAPPDATA%\Android\Sdk` 等

如果 Android Studio 能安装 App 但 launcher 找不到 adb，通常是因为环境变量未设置。可以通过以下方式解决：

**方式一：让 launcher 自动从 local.properties 查找**

确保项目根目录存在 `local.properties`（Android Studio 会自动生成），内容示例：

```properties
sdk.dir=D\:\\Android\\Sdk
```

launcher 会自动解析这个路径，找到 `D:\Android\Sdk\platform-tools\adb.exe`。

**方式二：手动选择 adb.exe**

在 launcher 的 USB 数据线连接区域，点击"选择 adb.exe"按钮，手动定位到：

```text
D:\Android\Sdk\platform-tools\adb.exe
```

选择后，launcher 会优先使用手动选择的路径。

### 手机 USB 模式报 127.0.0.1:8765 ECONNREFUSED

通常是 adb reverse 未成功建立，或电脑端 server 未启动。可手动执行以下命令检查：

```powershell
$adb = "D:\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb reverse tcp:8765 tcp:8765
& $adb reverse --list
```

确认输出包含 `UsbFfs tcp:8765 tcp:8765` 后再从手机连接。

### unauthorized

说明手机尚未授权 USB 调试。请查看手机屏幕上的授权弹窗，勾选允许后重新点击"一键配置 USB 连接"。

### offline

说明设备离线。请重新插拔数据线，确认 USB 调试已开启，然后重新查看 ADB 设备。

### 数据线只充电不传数据

更换支持数据传输的数据线，或在手机 USB 连接选项中选择文件传输/数据传输相关模式。

### 电脑端服务未启动

无论 LAN 还是 USB 模式，都需要先启动 `server.py`。请在 launcher 中点击“启动服务”。

### 手机端仍选了局域网模式

USB reverse 配置成功后，手机端还需要点击“修改”，选择“USB 数据线”，再点击“连接”。

### LAN 连接失败

请检查手机和电脑是否在同一 Wi-Fi、Windows 防火墙是否允许 Python 通信、手机端 IP 是否填写为电脑端显示的局域网 IP、端口是否为 `8765`。

## 注意事项

* USB 数据线模式依赖 adb、USB 调试授权和 `adb reverse`，不是原生 USB HID。
* 蓝牙、BLE、Android 原生 USB Host / Accessory 均未实现。
* PhoneType 不能读取电脑当前输入框内容，只能向电脑当前焦点发送文本或键盘事件。
* “退格”“Delete”“方向键”“复制”“剪切”等操作都作用于电脑当前焦点窗口。
* 使用前请确认电脑当前焦点位于目标输入框中。
* LAN 模式适合个人局域网环境使用，不建议暴露到公网。
* Windows 安全软件可能拦截 Python 模拟键盘输入。
