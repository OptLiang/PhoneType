# PhoneType v2.0 Release Checklist

用于 v2.0 stable 最终发布检查。所有检查完成后再打包归档。

## 编译检查

* Android Studio Gradle Sync 成功。
* 本机 PowerShell 执行 `.\gradlew.bat assembleDebug --stacktrace --no-daemon` 成功。
* `app/build.gradle.kts` 中 `versionName` 为 `2.0`。
* `app/build.gradle.kts` 中 `versionCode` 为 `3`，用于 stable 覆盖安装和分发识别。
* 未修改根项目 Gradle 插件版本、wrapper、版本目录或 SDK 配置。

## 运行检查

* `python launcher.py` 可打开 PC launcher。
* launcher 可启动 server。
* server 日志显示 `PhoneTypePC version: v2.0`。
* LAN 服务监听 `0.0.0.0:8765`。
* USB ADB reverse 提示和状态显示正常。

## 依赖检查

* 未新增 Android 依赖。
* 未新增 Python 依赖。
* `requirements.txt` 未修改。
* Android 端继续使用 `java.net.Socket` 和 `org.json.JSONObject`。
* Windows 端继续使用 `pyperclip` 和 `pynput`。

## 文档检查

* README 当前版本为 `v2.0 stable`。
* CHANGELOG 记录 v1.0 到 v2.0 stable 的主要阶段。
* README 包含 LAN 使用步骤。
* README 包含 USB ADB reverse 使用步骤。
* README 包含自动连接策略。
* README 包含延迟显示说明。
* README 明确蓝牙未实现。
* README 明确不建议公网暴露。
* 验收文档、真机测试清单、已知限制、发布清单均已存在。

## 安全检查

* JSON 协议字段未变化。
* key 白名单保留。
* shortcut 白名单保留。
* 不允许任意命令执行。
* 未新增 Android 权限。
* LAN 服务不建议暴露公网的提示保留。

## 真机检查

* LAN 手动连接通过。
* USB 手动连接通过。
* 自动连接 USB 优先场景通过。
* 自动连接 LAN 优先场景通过。
* 两种连接都失败时配置区展开并显示失败原因。
* 延迟显示正常。
* 小屏 UI 可访问底部按钮。
* 输入法弹出时点击“电脑控制”不被吞。

## 回归检查

* 中文、英文、emoji、多行文本输入正常。
* Enter 正常。
* Shift+Enter 正常。
* Backspace / Delete 正常。
* 全选 / 复制 / 剪切 / 粘贴 / 撤销正常。
* Win+Shift+S 截图正常。
* 方向键正常。
* 停 server 后 Android 显示失败且不崩溃。
* 拔 USB 后 Android 显示失败且不崩溃。
* LAN / USB 来回切换后旧连接释放。

## 发布前冻结项

* 不新增蓝牙、BLE 或原生 USB 功能。
* 不新增设置页。
* 不做大范围 UI 重构。
* 不修改协议字段。
* 不修改 Manifest。
* 不修改 requirements。
* 不修改 `app/src/main/res/**`。
* 不修改 Gradle 依赖或插件版本。

## Stable 最终确认项

* rc1 测试已通过。
* stable 未新增功能。
* versionName/versionCode 已更新。
* README / CHANGELOG / server 版本一致。
* 可以打包归档。
* 不新增功能入口。
* 不修改 JSON 协议字段。
* 不调整 key / shortcut 白名单。
* 不更换 LAN / USB 双通道技术路线。
* 不改动 Android 权限、Manifest、requirements 或资源目录。
