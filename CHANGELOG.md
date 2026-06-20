# Changelog

## v2.0

* v2.0 stable 从 rc1 通过测试后冻结。
* LAN TCP 长连接 + USB ADB reverse 双通道正式稳定。
* 未新增功能，保持 v2.0 范围冻结。
* 蓝牙 RFCOMM、BLE GATT 和原生 USB 留到 v2.1 或后续规划。

## v2.0-rc1

* 进入 v2.0 发布候选阶段。
* 统一 README、server 启动日志和 Android versionName / versionCode。
* 整理发布候选验收材料和 RC 冻结确认项。
* 保持 LAN + USB 双通道范围冻结，不新增蓝牙、BLE、原生 USB 或新协议字段。

## v2.0-beta1

* 进入 LAN + USB 双通道稳定冻结。
* 新增 v2.0 验收标准、真机测试清单、已知限制和发布清单。
* README 补充当前能力、LAN 使用步骤、USB ADB reverse 使用步骤、自动连接策略、延迟显示和已知限制。

## v2.0-alpha3

* Android 启动后按上次连接方式自动尝试 LAN / USB fallback。
* Android 本地统计 connect / ping / send 最近延迟并在顶部状态显示。
* 增强连接状态显示：已连接、未连接、连接失败、重连中。
* 小屏幕下连接配置区支持滚动，底部主按钮保持可访问。

## v2.0-alpha2-hotfix1

* 修复 PC launcher adb 查找能力，支持 local.properties、常见 SDK 路径和手动选择 adb.exe。
* 修复 USB ADB reverse 状态显示，使 launcher 能显示 USB reverse 连接模式。

## v2.0-alpha2

* 增加 USB ADB reverse 通道。
* 手机端 USB 模式通过 `127.0.0.1:8765` 连接电脑端服务。
* USB 与 LAN 复用同一 JSON Lines + TCP 协议。

## v2.0-alpha1

* 引入统一传输层。
* LAN 从短连接升级为 TCP 长连接。
* 连接管理集中到 Android 端统一管理器。

## v1.0

* LAN 短连接基础版。
* 支持 Android 输入文本并发送到 Windows 当前焦点。
* 电脑端通过 pyperclip 写入剪贴板，并通过 pynput 模拟键盘输入。
