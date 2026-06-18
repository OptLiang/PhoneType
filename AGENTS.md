# Codex 项目工作规则

本项目采用“项目经理 AI + 程序员 Agent + 用户测试员”协作模式。

Codex 作为程序员 Agent 工作时，必须优先使用 `programmer-agent` 技能。

## 本版目标

PhoneType v1.0 只实现“手机输入框 -> 局域网 TCP -> Windows 当前光标输入”的第一版目标。

本版只包含：

1. Android 手机端输入、预览文本并发送。
2. Windows Python TCP 服务端接收 JSON Lines 文本。
3. 服务端写入剪贴板并模拟 Ctrl+V，可选模拟 Enter。

## 本版不做

不做 AI、历史记录、剪贴板同步、蓝牙、鼠标触控板、账号系统、设置页、皮肤或任务外优化。

## 基本规则

1. 先读代码，再修改。
2. 只完成项目经理任务卡中的本版目标。
3. 不做任务外优化。
4. 不破坏已有功能保护清单。
5. 修改前必须说明准备修改的文件和原因。
6. 修改后必须给出修改摘要、风险点和测试清单。
7. 如果任务范围和实际代码位置冲突，必须暂停并说明需要授权的文件，不得越权修改。

## Android / Codex 构建限制

当前项目的编译验证以用户本机 Android Studio 或本机 PowerShell 为准。Codex 默认只负责源码修改和静态检查，不负责自动构建、安装或修环境。

除非用户在本轮任务中明确写出“允许 Codex 执行 Gradle 构建”，否则 Codex 禁止执行：

- `gradlew`
- `assembleDebug`
- `clean`
- `sdkmanager`
- `adb`
- Android SDK license 检查
- Android SDK 目录权限修复
- Gradle 缓存目录修复
- `D:\DevCache` 相关修复
- `.gradle` / `.gradle-user-home` / `build` 目录清理
- `local.properties` 修改

## 遇到环境错误时的处理

如果出现以下情况，Codex 必须立即停止环境排查，不得继续修 SDK 或 Gradle：

- `platforms/android-xx/package.xml` 拒绝访问
- SDK license 未接受
- Gradle wrapper `.lck` 拒绝访问
- `D:\DevCache` 权限问题
- adb 无法访问设备
- Codex 构建结果与 Android Studio / 本机 PowerShell 结果不一致

此时只能输出：

```text
当前 Codex 执行环境无法稳定访问本机 Android SDK/Gradle/adb。
源码修改已完成，请用户在 Android Studio 或本机 PowerShell 编译。
```

## 交付要求

每轮结束必须输出：

1. 实际保留的源码改动。
2. 每个文件修改了什么。
3. 是否改动任务外内容。
4. 是否触碰构建/环境文件。
5. 建议用户本机执行的编译命令。
6. 真机测试清单。

建议给用户的 Android 本机编译命令：

```powershell
cd C:\Users\61028\AndroidStudioProjects\PhoneType
$env:GRADLE_USER_HOME="$PWD\.gradle-user-home"
.\gradlew.bat assembleDebug --stacktrace --no-daemon
```
