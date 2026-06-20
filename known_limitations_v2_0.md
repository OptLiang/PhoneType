# PhoneType v2.0 Known Limitations

本文档列出 v2.0 stable 明确保留的限制，不视为本版本缺陷。

## USB 依赖 adb 和 USB 调试

USB 数据线模式依赖 Android SDK Platform-Tools 中的 adb，并要求手机开启 USB 调试、完成授权、成功建立 `adb reverse tcp:8765 tcp:8765`。

## USB 不是原生 USB HID

USB 数据线模式不是 Android 原生 USB Host / Accessory，也不是 USB HID 键盘。它仍然复用 JSON Lines + TCP 协议，只是通过 adb reverse 把手机端 `127.0.0.1:8765` 转发到电脑端服务。

## 蓝牙未实现

v2.0 不支持蓝牙 RFCOMM，不支持 BLE GATT，也不提供蓝牙自动切换。

## 不能读取电脑当前输入框内容

PhoneType 只能向电脑当前焦点发送文本和键盘事件，不能读取、同步或解析电脑当前输入框中的已有内容。

## 只作用于电脑当前焦点

文本输入、Enter、快捷键和方向键都作用于 Windows 当前焦点窗口。如果焦点不在目标输入框中，输入可能进入其他窗口。

## 不建议公网暴露

LAN TCP 服务面向个人局域网使用，不建议暴露到公网或不可信网络。

## Windows 安全软件可能拦截 pynput

部分安全软件、输入保护软件或受限应用可能阻止 Python 模拟键盘事件，导致电脑端无法输入或快捷键无效。

## 多设备 adb 场景需要保留一个设备

launcher 的一键 USB 配置按单设备场景设计。如果 adb 同时看到多个在线设备，应只保留一个设备后再配置 reverse。

## Android 厂商系统可能影响后台连接

部分 Android 厂商系统可能在息屏、后台或省电模式下限制网络连接。发布前真机测试应覆盖目标设备的前台和短暂后台场景。
