# PhoneType v2.0 Real Device Test Checklist

用于 v2.0 stable 真机验收。每项通过后在测试结果填写区记录设备、系统版本、测试人、日期和结论。

## 测试结果填写区

* 测试设备：
* Android 系统版本：
* Windows 系统版本：
* PhoneType 版本：v2.0
* 测试人：
* 测试日期：
* 测试结论：通过 / 有条件通过 / 不通过
* 备注：

## 测试环境

* Windows 10 或 Windows 11 电脑。
* Android 真机一台。
* Python 运行环境可启动 `PhoneTypePC/launcher.py`。
* 已安装 Python 依赖：`pyperclip`、`pynput`。
* Android SDK Platform-Tools 可用，USB 测试时 adb 可访问设备。
* LAN 测试时手机和电脑在同一局域网。

## 电脑端启动

* 进入 `D:\AndroidStudioProjects\PhoneType\PhoneTypePC`。
* 运行 `python launcher.py`。
* 点击“启动服务”。
* 确认 launcher 显示本机地址、服务状态、最近客户端、最近操作。

## Android 安装

* 使用 Android Studio 或本机 PowerShell 编译安装 App。
* 首次打开 App 后确认页面显示 PhoneType、当前连接状态、连接配置、输入框和底部按钮。

## LAN 手动连接

* 点击“修改”。
* 选择“局域网”。
* 输入 launcher 显示的电脑 IP 和端口 `8765`。
* 点击“连接”。
* 预期：顶部显示“当前连接：局域网 · 已连接 · xx ms”。

## USB 手动连接

* 手机通过 USB 数据线连接电脑。
* 手机开启开发者选项和 USB 调试。
* launcher 点击“一键配置 USB 连接”。
* 如果手机弹出授权，点击允许。
* Android 端点击“修改”，选择“USB 数据线”，点击“连接”。
* 预期：顶部显示“当前连接：USB 数据线 · 已连接 · xx ms”。

## 自动连接

* 上次模式为 USB，USB 可用时重启 App，确认自动连接 USB。
* 上次模式为 USB，USB 不可用但 LAN 可用时重启 App，确认自动尝试 LAN。
* 上次模式为 LAN，LAN 可用时重启 App，确认自动连接 LAN。
* 上次模式为 LAN，LAN 不可用但 USB 可用时重启 App，确认自动尝试 USB。
* 两者不可用时重启 App，确认展开连接配置区并显示失败原因。
* 手动连接 LAN 失败时，确认不会自动跳 USB。
* 手动连接 USB 失败时，确认不会自动跳 LAN。

## 文本输入

* 输入中文文本并点击“输入”。
* 输入英文文本并点击“输入”。
* 输入 emoji 并点击“输入”。
* 输入多行文本并点击“输入”。
* 预期：电脑当前焦点处收到文本，Android 输入框在成功后清空。

## Enter / Shift+Enter

* 点击底部“发送”。
* 预期：电脑当前焦点收到 Enter。
* 打开“电脑控制”，点击“换行”。
* 预期：电脑当前焦点收到 Shift+Enter。

## 退格 / Delete

* 点击“退格”。
* 预期：电脑当前焦点执行 Backspace。
* 点击“Delete”。
* 预期：电脑当前焦点执行 Delete。

## 全选 / 复制 / 剪切 / 粘贴 / 撤销

* 点击“全选”，预期 Ctrl+A 正常。
* 点击“复制”，预期 Ctrl+C 正常。
* 点击“剪切”，预期 Ctrl+X 正常。
* 点击“粘贴”，预期 Ctrl+V 正常。
* 点击“撤销”，预期 Ctrl+Z 正常。

## 截图

* 点击“截图”。
* 预期：Windows 触发 Win+Shift+S 截图。

## 方向键

* 点击底部“方向”。
* 分别点击上、下、左、右。
* 预期：电脑当前焦点光标按方向移动。
* 点击“退出”后，方向区关闭。

## 拔 USB

* USB 连接成功后拔掉数据线。
* 点击发送或任一控制按钮。
* 预期：Android 显示失败，不崩溃。

## 停 server

* LAN 或 USB 连接成功后，在 launcher 停止服务。
* 点击发送或任一控制按钮。
* 预期：Android 显示失败，不崩溃。

## 切换 LAN / USB

* LAN 连接成功后切换到 USB。
* USB 连接成功后切换到 LAN。
* 预期：切换后旧连接释放，状态变为“未连接”，手动连接只连接当前模式。

## 小屏 UI

* 在小屏竖屏设备上展开连接配置区。
* 预期：底部“输入 / 方向 / 发送”始终可访问。
* 配置区内容过高时可局部滚动。
* 最近操作文字不遮挡底部按钮。

## 输入法弹出时电脑控制按钮

* 聚焦输入框并弹出输入法。
* 点击“电脑控制”。
* 预期：输入法先收起，电脑控制区随后可见，按钮点击不被吞。
