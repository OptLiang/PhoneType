# Codex 项目工作规则

本项目采用“项目经理 AI + 程序员 Agent + 用户测试员”协作模式。

Codex 作为程序员 Agent 工作时，必须优先使用 `programmer-agent` 技能。

## 基本规则

1. 先读代码，再修改。
2. 只完成项目经理任务卡中的本版目标。
3. 不做任务外优化。
4. 不破坏已有功能保护清单。
5. 修改前必须说明准备修改的文件和原因。
6. 修改后必须给出修改摘要、风险点和测试清单。
7. 如果任务范围和实际代码位置冲突，必须暂停并说明需要授权的文件，不得越权修改。

## 技术选型与迭代效率原则

1. 优先复用项目已有代码、已有结构和 Python 标准库；标准库不能满足时，优先选择成熟、维护活跃、文档清晰的开源库。
2. 新增库或新增功能前必须先判断是否值得做：说明必要性、对当前版本推进的效果、测试成本、维护成本和是否会拖慢交付。
3. 不为小问题引入大依赖，不为短期便利改变技术栈，不用新实现覆盖已有稳定逻辑。
4. 如果确需新增依赖，必须同步说明依赖名称、用途、安装方式、是否需要写入 `requirements.txt`，并给出不引入该依赖的替代方案。
5. 为节省用量，每轮迭代应在升级目标明确、方便测试、风险可控的前提下，尽可能合并完成同一方向的相关改动，避免把强相关的小改动拆成多轮。
6. “尽可能多做”不能突破本轮授权范围，不能牺牲可测试性，不能破坏旧功能保护清单。
7. 每轮交付必须给出可直接执行的测试清单，让用户能快速确认本轮改动是否达标。

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

## Windows / PowerShell 命令执行规范

本项目在 Windows 环境下运行，存在中文文件名和中文路径。Codex 执行命令时必须遵守：

1. 不要在 PowerShell 的 -c 参数、多层引号或管道命令中硬编码中文文件名。
2. 如需处理中文文件名，优先让 Python 在程序内部使用 pathlib / glob / os.listdir 自动发现文件。
3. 复杂 Python 测试代码不要通过 PowerShell 多层引号直接传递；优先使用临时目录内的纯 ASCII 临时测试文件，或用 Python 自己扫描目标文件。
4. 一个测试命令失败时，先判断是 shell 引号、编码、路径、工作目录问题，不能直接归因于权限不足。
5. 除非明确出现 Access denied / 拒绝访问 / PermissionError，否则不得请求完全控制。
6. 如果只是 py.exe 找不到解释器、PowerShell 引号解析失败、中文文件名乱码、git 非仓库状态，必须换测试方式，不得申请更高权限。
7. 本项目不允许因为测试命令失败而修改系统环境、Python 安装、PowerShell 配置或请求完全控制。


## 测试命令失败与权限判断

1. 源码修改失败才优先排查权限；测试命令失败必须先排查命令写法、引号、编码、路径和工作目录。
2. 只有明确出现 `PermissionError`、`Access denied`、`拒绝访问`，或任务确实需要写入受保护目录、安装依赖、改系统环境时，才允许请求更高权限。
3. `py.exe` 找不到解释器、PowerShell 引号解析失败、中文文件名乱码、`git diff` 因非仓库失败、临时测试脚本未跑通，都不属于请求完全控制的理由。
4. 测试代码应优先使用临时目录和纯 ASCII 临时文件；必须操作中文文件名时，让 Python 在程序内部通过 `pathlib` / `glob` 自动发现。
5. 测试失败但源码静态检查已通过时，应如实说明已完成的检查和未完成的验证，不得把环境问题包装成源码问题。

## 交付要求

每轮结束必须输出：

1. 实际保留的源码改动。
2. 每个文件修改了什么。
3. 是否改动任务外内容。
4. 是否触碰构建/环境文件。
5. 建议用户本机执行的编译命令。
6. 测试清单；Android 项目需要给出真机测试清单。

编译/检查命令必须按项目类型输出：

- Python 项目：优先输出 `python -m py_compile ...`、已有测试命令或最小可运行命令。
- Android/Kotlin 项目：只有当前项目确认为 Android/Kotlin 项目时，才输出 Android 本机编译命令。
- 非 Android 项目禁止机械附带 Android / Gradle / adb 命令。

Android/Kotlin 项目建议给用户的本机编译命令：

```powershell
cd C:\Users\61028\AndroidStudioProjects\LearnFlow
$env:GRADLE_USER_HOME="$PWD\.gradle-user-home"
.\gradlew.bat assembleDebug --stacktrace --no-daemon
```
