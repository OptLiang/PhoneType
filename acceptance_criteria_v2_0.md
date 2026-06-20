# PhoneType v2.0 Acceptance Criteria

本文档适用于 v2.0 stable 最终验收。v2.0 范围冻结为 LAN TCP 长连接 + USB ADB reverse 双通道，不包含蓝牙、BLE、原生 USB HID、云同步、历史记录或剪贴板同步。

## LAN 连接验收

* 电脑端启动 `PhoneTypePC/server.py` 或通过 `PhoneTypePC/launcher.py` 启动服务。
* Android 端选择“局域网”，输入电脑端显示的 LAN IP 和端口。
* 点击“连接”后，顶部显示“当前连接：局域网 · 已连接 · xx ms”。
* 连接成功后复用同一个 TCP 长连接发送多次请求。
* 修改 LAN IP 或端口后，Android 端状态变为“未连接”。

## USB 连接验收

* 电脑端 launcher 能找到 adb，包含 PATH、ANDROID_HOME、ANDROID_SDK_ROOT、local.properties、常见兜底路径和手动选择 adb.exe。
* 点击“一键配置 USB 连接”后，adb reverse 建立 `tcp:8765 -> tcp:8765`。
* Android 端选择“USB 数据线”，点击“连接”后，顶部显示“当前连接：USB 数据线 · 已连接 · xx ms”。
* USB 模式只通过 `127.0.0.1:8765` 的 TCP 包装实现，不使用 Android 原生 USB API。

## 自动连接验收

* 上次模式为 USB 且 USB 可用时，App 启动自动连接 USB。
* 上次模式为 USB、USB 不可用但已保存 LAN 且 LAN 可用时，App 启动后自动尝试 LAN。
* 上次模式为 LAN 且 LAN 可用时，App 启动自动连接 LAN。
* 上次模式为 LAN、LAN 不可用但 USB 可用时，App 启动后自动尝试 USB。
* 两种方式都不可用时，App 展开连接配置区并显示失败原因。
* 用户手动点击“连接”时只连接当前选择模式，不自动 fallback 到另一个模式。

## 延迟显示验收

* connect 成功后显示最近连接延迟。
* ping 成功后刷新最近延迟。
* send 成功后刷新最近延迟。
* 失败状态不显示“已连接 · xx ms”。
* 延迟由 Android 端本地计算，不要求电脑端返回延迟，不修改 JSON 字段。

## 输入文本验收

* 中文输入正常。
* 英文输入正常。
* emoji 输入正常。
* 多行文本输入正常。
* 输入成功后 Android 输入框清空。
* 电脑端使用 pyperclip 写入剪贴板，再通过 pynput 模拟 Ctrl+V。

## 快捷键验收

* Enter 发送正常。
* Shift + Enter 换行正常。
* Backspace 正常。
* Delete 正常。
* Ctrl+A 全选正常。
* Ctrl+C 复制正常。
* Ctrl+X 剪切正常。
* Ctrl+V 粘贴正常。
* Ctrl+Z 撤销正常。
* Win+Shift+S 截图正常。
* 上、下、左、右方向键正常。

## 安全边界验收

* JSON 协议字段保持 `type`、`text`、`enter`、`key`、`keys`。
* 服务端只接受内置 key 白名单。
* 服务端只接受内置 shortcut 白名单。
* 不允许客户端执行任意命令。
* 不新增 Android 权限。
* 不新增 Python 依赖。
* 不建议将 LAN 服务暴露到公网。

## 异常处理验收

* 电脑端服务停止后，Android 发送请求显示失败且不崩溃。
* USB 拔线或 adb reverse 失效后，Android 发送请求显示失败且不崩溃。
* LAN IP 或端口错误时，连接失败信息可见。
* LAN / USB 来回切换后旧连接释放。
* App 页面销毁时主动断开当前连接。
* launcher 遇到 unauthorized、offline、多设备或未找到 adb 时给出明确提示。
