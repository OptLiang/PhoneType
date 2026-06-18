# PhoneType

PhoneType 是一个“手机输入法电脑输入器”。它允许用户在 Android 手机上使用手机输入法输入、预览文本，并通过局域网 TCP 将内容输入到 Windows 电脑当前光标位置。它适合在微信、网页输入框、文档编辑器、聊天窗口等场景中使用手机输入法辅助电脑输入。

## 功能特点

* Android 手机端输入文本并预览
* 一键将手机输入内容输入到电脑当前光标位置
* 支持中文、英文、emoji、多行文本
* 支持电脑端 Enter 发送
* 支持 Shift + Enter 换行
* 支持远程方向键控制光标移动
* 支持退格、Delete、全选、复制、剪切、粘贴、撤销
* 支持 Win + Shift + S 触发 Windows 截图
* 手机端自动记忆上次电脑 IP 和端口
* App 启动后自动尝试连接上次电脑地址
* 电脑端提供 GUI 监听启动器，可双击 `launcher.pyw` 打开
* 电脑端启动器显示本机 IP、端口和监听状态
* 无需浏览器插件、无需云服务、无需账号系统

## 项目结构

```text
PhoneType
├── app
│   └── src/main
│       ├── AndroidManifest.xml
│       ├── java/com/phonetype
│       │   ├── MainActivity.kt
│       │   └── TcpClient.kt
│       └── res
│           ├── drawable
│           ├── mipmap-anydpi-v26
│           ├── mipmap-hdpi
│           ├── mipmap-mdpi
│           ├── mipmap-xhdpi
│           ├── mipmap-xxhdpi
│           ├── mipmap-xxxhdpi
│           └── values
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

## 运行环境

### Android 端

* Android Studio
* Android SDK 35
* Kotlin 2.0.21
* Android Gradle Plugin 8.6.1
* Android 设备和电脑处于同一局域网

### Windows 电脑端

* Windows 10 / Windows 11
* Python 3.10 或更高版本
* 局域网环境

## 电脑端使用方法

进入电脑端目录：

```powershell
cd (保存位置)\PhoneType\PhoneTypePC
```

安装依赖：

```powershell
pip install -r requirements.txt
```

启动图形界面监听器：

```powershell
python launcher.py
```

也可以直接双击：

```text
PhoneTypePC\launcher.pyw
```

启动后，窗口会显示本机局域网 IP 和端口，例如：

```text
本机地址：192.168.1.8:8765
服务状态：后台监听中
```

手机端填写该 IP 和端口后即可连接。

## Android 端使用方法

1. 使用 Android Studio 打开项目根目录：

```text
(保存位置)\PhoneType
```

2. 等待 Gradle Sync 完成。

3. 连接 Android 手机。

4. 运行 `app` 到手机。

5. 打开 PhoneType App。

6. 如果是第一次使用，输入电脑端显示的 IP 和端口。

7. 点击测试连接。

8. 连接成功后，在输入框输入文本，点击“输入”。

## 手机端按钮说明

### 输入

将手机输入框中的内容输入到电脑当前光标位置。输入成功后，手机输入框会自动清空。

### 发送

向电脑发送 Enter。适合微信、网页聊天框、评论框等“Enter 发送”的场景。

### 方向

打开方向轮盘，用于控制电脑光标上下左右移动。再次点击中心按钮可退出方向模式。

### 换行

发送 Shift + Enter。适合在聊天框中换行而不是发送消息。

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

## 通信协议

PhoneType 使用局域网 TCP 通信。默认端口：

```text
8765
```

通信格式为 JSON Lines，每条消息一行 JSON。

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

## 常见问题

### 1. 手机连接失败

请检查：

* 手机和电脑是否连接同一个 Wi-Fi
* 电脑端 `launcher.pyw` 是否已经启动监听
* Windows 防火墙是否允许 Python 通信
* 手机端 IP 是否填写为电脑端显示的局域网 IP
* 端口是否为 `8765`

### 2. 手机显示连接正常，但电脑没有输入文字

请检查：

* 电脑当前焦点是否在输入框中
* 是否有安全软件拦截 Python 模拟键盘
* 是否正在运行多个旧版 `server.py`
* 电脑端监听器是否显示后台监听中

### 3. 停止服务后手机还能输入

说明 8765 端口上可能仍有旧服务进程。可以在 PowerShell 中检查：

```powershell
netstat -ano | findstr :8765
```

如有旧进程，可以通过 PhoneType PC 启动器停止服务，或手动结束对应 PID。

### 4. App 图标没有更新

请确认 `AndroidManifest.xml` 中包含：

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

然后重新安装 App。

## 注意事项

* PhoneType 当前版本基于局域网 TCP，不是蓝牙 HID 键盘。
* PhoneType 不能读取电脑当前输入框内容，只能向电脑当前焦点发送文本或键盘事件。
* “退格”“Delete”“方向键”“复制”“剪切”等操作都作用于电脑当前焦点窗口。
* 使用前请确认电脑当前焦点位于目标输入框中。
* 本项目适合个人局域网环境使用，不建议暴露到公网。

## 下载

可在 GitHub Releases 页面下载：

- `PhoneType-v1.0.0-debug.apk`：Android 手机端
- `PhoneTypePC-v1.0.0.zip`：Windows 电脑端监听器
