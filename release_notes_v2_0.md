# PhoneType v2.0 Release Notes

PhoneType v2.0 是 LAN + USB 双通道稳定版。本版本从 v2.0-rc1 真机测试通过后冻结，不新增功能，不修改 JSON 协议字段，不调整 key / shortcut 白名单。

## 核心能力

* LAN TCP 长连接：连接成功后复用同一个 TCP socket 连续发送 JSON Lines 请求。
* USB ADB reverse：通过 `adb reverse tcp:8765 tcp:8765`，Android 端连接 `127.0.0.1:8765`。
* 启动自动连接：优先尝试上次连接方式，失败后尝试备用 LAN / USB。
* 最近延迟显示：Android 本地统计 connect / ping / send 成功耗时。
* 文本输入：支持中文、英文、emoji 和多行文本。
* 电脑控制：支持 Enter、Shift+Enter、Backspace、Delete、全选、复制、剪切、粘贴、撤销、截图和方向键。

## 使用方式

1. 在 Windows 电脑端运行 `PhoneTypePC/launcher.py`。
2. 点击“启动服务”。
3. LAN 模式下，在 Android 端选择“局域网”，输入电脑端显示的 IP 和端口后连接。
4. USB 模式下，先在 launcher 点击“一键配置 USB 连接”，再在 Android 端选择“USB 数据线”并连接。
5. 连接成功后，Android 顶部显示当前通道、连接状态和最近延迟。

## 从 v1.0 到 v2.0 的主要变化

* v1.0 的 LAN 短连接升级为 LAN TCP 长连接。
* 增加统一传输层和连接管理。
* 增加 USB ADB reverse 通道。
* 增加启动自动连接 LAN / USB fallback。
* 增加 Android 本地延迟显示。
* 增加小屏连接配置区滚动稳定化。
* 增加 v2.0 验收标准、真机测试清单、已知限制、发布清单和 CHANGELOG。

## 已知限制

* USB 数据线模式依赖 adb、USB 调试授权和 adb reverse，不是原生 USB HID。
* 蓝牙 RFCOMM、BLE GATT 和 Android 原生 USB Host / Accessory 未包含在 v2.0。
* PhoneType 不能读取电脑当前输入框内容，只能向电脑当前焦点发送文本或键盘事件。
* LAN TCP 服务面向个人局域网使用，不建议暴露到公网。
* Windows 安全软件可能拦截 pynput 模拟键盘输入。

## 不包含内容

* 蓝牙和 BLE。
* Android 原生 USB。
* 云同步、历史记录、剪贴板同步。
* 鼠标触控板。
* 新 Android 权限或新 Python 依赖。
