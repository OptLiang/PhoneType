import os
import queue
import shutil
import socket
import subprocess
import sys
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox


PORT = 8765
LOCAL_CHECK_HOST = "127.0.0.1"
LOCAL_IPS = {"127.0.0.1", "localhost", "::1"}


def get_lan_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "127.0.0.1"


def is_port_open(host, port, timeout=0.4):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def is_port_listening(port):
    return is_port_open(LOCAL_CHECK_HOST, port)


def wait_until_port_closed(port, timeout=1.0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if not is_port_listening(port):
            return True
        time.sleep(0.1)
    return not is_port_listening(port)


def hidden_creationflags():
    if os.name == "nt":
        return getattr(subprocess, "CREATE_NO_WINDOW", 0)
    return 0


def run_hidden(args):
    kwargs = {
        "capture_output": True,
        "text": True,
        "encoding": "utf-8",
        "errors": "replace",
    }
    if os.name == "nt":
        kwargs["creationflags"] = hidden_creationflags()

    try:
        return subprocess.run(args, **kwargs)
    except TypeError:
        kwargs.pop("encoding", None)
        kwargs.pop("errors", None)
        return subprocess.run(args, **kwargs)


def adb_executable_name():
    return "adb.exe" if os.name == "nt" else "adb"


def decode_gradle_properties_path(value):
    """将 gradle local.properties 中的转义路径还原为真实文件系统路径。

    例如：
        D\\:\\\\Android\\\\Sdk  ->  D:\\Android\\Sdk
    """
    value = value.strip()
    value = value.replace("\\:", ":")
    value = value.replace("\\\\", "\\")
    return value


def _find_adb_from_local_properties():
    """从项目根目录 local.properties 的 sdk.dir 查找 adb。"""
    adb_name = adb_executable_name()
    try:
        project_root = Path(__file__).resolve().parent.parent
        props_path = project_root / "local.properties"
        if not props_path.is_file():
            return None
        content = props_path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None

    for line in content.splitlines():
        line = line.strip()
        if line.startswith("#") or "=" not in line:
            continue
        key, _, raw_value = line.partition("=")
        if key.strip() != "sdk.dir":
            continue
        sdk_dir = decode_gradle_properties_path(raw_value)
        candidate = Path(sdk_dir) / "platform-tools" / adb_name
        if candidate.is_file():
            return str(candidate)

    return None


def _fallback_adb_candidates():
    """返回一组兜底 adb 候选路径（不依赖环境变量，不报错）。"""
    adb_name = adb_executable_name()
    candidates = []

    # 常见硬编码路径
    candidates.append(Path("D:/Android/Sdk/platform-tools") / adb_name)
    candidates.append(Path("D:/AndroidSDK/platform-tools") / adb_name)
    candidates.append(Path("D:/Dev/Android/Sdk/platform-tools") / adb_name)

    # %LOCALAPPDATA%/Android/Sdk
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        candidates.append(Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / adb_name)

    # 当前用户常见目录
    home_dir = Path.home()
    candidates.append(home_dir / "AppData" / "Local" / "Android" / "Sdk" / "platform-tools" / adb_name)

    return candidates


def find_adb(selected_adb_path=None):
    """查找 adb 可执行文件路径。

    查找顺序：
    1. 用户手动选择的 adb 路径
    2. PATH 中的 adb
    3. ANDROID_HOME/platform-tools/adb.exe
    4. ANDROID_SDK_ROOT/platform-tools/adb.exe
    5. local.properties 中的 sdk.dir
    6. 常见兜底路径
    """
    adb_name = adb_executable_name()

    # 第 1 优先级：用户手动选择的路径
    if selected_adb_path and Path(selected_adb_path).is_file():
        return selected_adb_path

    # 第 2 优先级：PATH
    path_adb = shutil.which(adb_name) or shutil.which("adb")
    if path_adb:
        return path_adb

    # 第 3-4 优先级：ANDROID_HOME / ANDROID_SDK_ROOT
    for env_name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk_root = os.environ.get(env_name)
        if sdk_root:
            candidate = Path(sdk_root) / "platform-tools" / adb_name
            if candidate.is_file():
                return str(candidate)

    # 第 5 优先级：local.properties
    adb_from_props = _find_adb_from_local_properties()
    if adb_from_props:
        return adb_from_props

    # 第 6 优先级：兜底路径
    for candidate in _fallback_adb_candidates():
        if candidate.is_file():
            return str(candidate)

    return None


def parse_adb_devices(output):
    devices = []
    for line in output.splitlines():
        text = line.strip()
        if not text or text.startswith("List of devices"):
            continue
        if text.startswith("* daemon"):
            continue

        parts = text.split()
        if len(parts) >= 2:
            devices.append((parts[0], parts[1]))

    return devices


def get_windows_listening_pids(port):
    if os.name != "nt":
        return []

    result = run_hidden(["netstat", "-ano", "-p", "tcp"])
    if result.returncode != 0:
        return []

    port_suffix = f":{port}"
    pids = []
    for line in result.stdout.splitlines():
        parts = line.split()
        if len(parts) < 5:
            continue

        proto = parts[0].upper()
        local_address = parts[1]
        state = parts[-2].upper()
        pid = parts[-1]

        if proto != "TCP" or state != "LISTENING":
            continue
        if not local_address.endswith(port_suffix):
            continue
        if pid.isdigit() and pid not in pids:
            pids.append(pid)

    return pids


class PhoneTypeLauncher:
    def __init__(self, root):
        self.root = root
        self.process = None
        self.log_queue = queue.Queue()
        self.base_dir = Path(__file__).resolve().parent
        self.server_path = self.base_dir / "server.py"
        self.selected_adb_path = None

        self.root.title("PhoneType PC")
        self.root.geometry("500x480")
        self.root.resizable(False, False)
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

        self.status_var = tk.StringVar(value="未启动")
        self.address_var = tk.StringVar(value=f"{get_lan_ip()}:{PORT}")
        self.recent_var = tk.StringVar(value="等待手机连接")
        self.mode_var = tk.StringVar(value="LAN TCP")
        self.client_var = tk.StringVar(value="暂无")
        self.operation_var = tk.StringVar(value="暂无")
        self.usb_status_var = tk.StringVar(value="未配置")
        self.adb_path_var = tk.StringVar(value="未检测")

        self.build_ui()
        self.refresh_adb_path_display()
        self.refresh_initial_port_state()
        self.update_buttons()
        self.poll_events()

    def build_ui(self):
        container = tk.Frame(self.root, padx=20, pady=16)
        container.pack(fill=tk.BOTH, expand=True)

        title = tk.Label(container, text="PhoneType PC", font=("Segoe UI", 18, "bold"))
        title.pack(anchor="w")

        self.address_text = tk.Label(
            container,
            text=f"本机地址：{self.address_var.get()}",
            font=("Microsoft YaHei UI", 11),
        )
        self.address_text.pack(anchor="w", pady=(18, 0))

        self.status_text = tk.Label(
            container,
            text=f"服务状态：{self.status_var.get()}",
            font=("Microsoft YaHei UI", 11),
        )
        self.status_text.pack(anchor="w", pady=(8, 0))

        self.mode_text = tk.Label(
            container,
            text=f"连接模式：{self.mode_var.get()}",
            font=("Microsoft YaHei UI", 10),
        )
        self.mode_text.pack(anchor="w", pady=(8, 0))

        self.client_text = tk.Label(
            container,
            text=f"最近客户端：{self.client_var.get()}",
            font=("Microsoft YaHei UI", 10),
            anchor="w",
            justify="left",
            wraplength=440,
        )
        self.client_text.pack(anchor="w", fill=tk.X, pady=(8, 0))

        self.operation_text = tk.Label(
            container,
            text=f"最近操作：{self.operation_var.get()}",
            font=("Microsoft YaHei UI", 10),
            anchor="w",
            justify="left",
            wraplength=440,
        )
        self.operation_text.pack(anchor="w", fill=tk.X, pady=(8, 0))

        self.recent_text = tk.Label(
            container,
            text=f"最近状态：{self.recent_var.get()}",
            font=("Microsoft YaHei UI", 10),
            anchor="w",
            justify="left",
            wraplength=440,
        )
        self.recent_text.pack(anchor="w", fill=tk.X, pady=(8, 0))

        usb_frame = tk.LabelFrame(
            container,
            text="USB 数据线连接",
            font=("Microsoft YaHei UI", 10),
            padx=8,
            pady=8,
        )
        usb_frame.pack(anchor="w", fill=tk.X, pady=(12, 0))

        self.adb_path_text = tk.Label(
            usb_frame,
            text=f"ADB：{self.adb_path_var.get()}",
            font=("Microsoft YaHei UI", 9),
            anchor="w",
            justify="left",
            wraplength=440,
        )
        self.adb_path_text.pack(anchor="w", fill=tk.X, pady=(0, 4))

        usb_button_row = tk.Frame(usb_frame)
        usb_button_row.pack(anchor="w")

        self.configure_usb_button = tk.Button(
            usb_button_row,
            text="一键配置 USB 连接",
            width=16,
            command=self.configure_usb_reverse,
        )
        self.configure_usb_button.pack(side=tk.LEFT)

        self.adb_devices_button = tk.Button(
            usb_button_row,
            text="查看 ADB 设备",
            width=12,
            command=self.show_adb_devices,
        )
        self.adb_devices_button.pack(side=tk.LEFT, padx=(8, 0))

        self.clear_usb_button = tk.Button(
            usb_button_row,
            text="清除 USB 转发",
            width=12,
            command=self.clear_usb_reverse,
        )
        self.clear_usb_button.pack(side=tk.LEFT, padx=(8, 0))

        self.select_adb_button = tk.Button(
            usb_button_row,
            text="选择 adb.exe",
            width=12,
            command=self.choose_adb_path,
        )
        self.select_adb_button.pack(side=tk.LEFT, padx=(8, 0))

        self.usb_status_text = tk.Label(
            usb_frame,
            text=f"状态：{self.usb_status_var.get()}",
            font=("Microsoft YaHei UI", 10),
            anchor="w",
            justify="left",
            wraplength=440,
        )
        self.usb_status_text.pack(anchor="w", fill=tk.X, pady=(8, 0))

        button_row = tk.Frame(container)
        button_row.pack(anchor="w", pady=(16, 0))

        self.start_button = tk.Button(
            button_row,
            text="启动服务",
            width=12,
            command=self.start_service,
        )
        self.start_button.pack(side=tk.LEFT)

        self.stop_button = tk.Button(
            button_row,
            text="停止服务",
            width=12,
            command=self.stop_service,
        )
        self.stop_button.pack(side=tk.LEFT, padx=(12, 0))

    def refresh_initial_port_state(self):
        if is_port_listening(PORT):
            self.set_status("端口被占用")
            self.set_recent(f"端口 {PORT} 已被占用，可能已有旧服务运行")

    def set_status(self, status):
        self.status_var.set(status)
        self.status_text.config(text=f"服务状态：{status}")

    def set_recent(self, message):
        if message:
            self.recent_var.set(message)
            self.recent_text.config(text=f"最近状态：{message}")

    def set_recent_client(self, client):
        if client:
            self.client_var.set(client)
            self.client_text.config(text=f"最近客户端：{client}")

    def set_recent_operation(self, operation):
        if operation:
            self.operation_var.set(operation)
            self.operation_text.config(text=f"最近操作：{operation}")

    def set_usb_status(self, message):
        if message:
            self.usb_status_var.set(message)
            self.usb_status_text.config(text=f"状态：{message}")

    def handle_server_output(self, line):
        if line.startswith("client connected: "):
            client = line.split(": ", 1)[1]
            self.set_recent_client(client)
            self._update_mode_from_client(client)
            return

        if line.startswith("client disconnected: "):
            client = line.split(": ", 1)[1]
            self.set_recent_client(f"{client} 已断开")
            return

        if line.startswith("request "):
            self.set_recent_operation(line)

    def _update_mode_from_client(self, client):
        # client 格式: "127.0.0.1:51234" 或 "192.168.1.8:51234"
        parts = client.rsplit(":", 1)
        ip = parts[0] if parts else client
        if ip.lower() in LOCAL_IPS:
            self.set_mode("USB ADB reverse")
        else:
            self.set_mode("LAN TCP")

    def get_adb_or_report(self):
        adb_path = find_adb(self.selected_adb_path)
        if adb_path:
            return adb_path

        # 收集已尝试的路径用于诊断
        tried = []
        tried.append(f"PATH: {shutil.which(adb_executable_name()) or '未找到'}")
        for env_name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
            sdk_root = os.environ.get(env_name)
            tried.append(f"{env_name}: {sdk_root or '未设置'}")
        tried.append(f"local.properties: {'已检查' if Path(__file__).resolve().parent.parent.joinpath('local.properties').is_file() else '未找到'}")
        tried.append(f"兜底路径: D:\\Android\\Sdk, LOCALAPPDATA\\Android\\Sdk 等")

        message = "找不到 adb，请安装 Android SDK Platform-Tools 或配置 ANDROID_HOME\n\n已尝试的路径：\n" + "\n".join(tried)
        self.set_usb_status("ADB：未找到")
        self.set_recent(message)
        messagebox.showwarning("PhoneType PC", message)
        return None

    def refresh_adb_path_display(self):
        adb_path = find_adb(self.selected_adb_path)
        if adb_path:
            self.adb_path_var.set(adb_path)
        else:
            self.adb_path_var.set("未找到")
        self.adb_path_text.config(text=f"ADB：{self.adb_path_var.get()}")

    def choose_adb_path(self):
        path = filedialog.askopenfilename(
            title="选择 adb.exe",
            filetypes=[("adb executable", "adb.exe"), ("All files", "*.*")],
        )
        if not path:
            return
        if not Path(path).is_file():
            messagebox.showwarning("PhoneType PC", f"文件不存在：{path}")
            return
        self.selected_adb_path = path
        self.refresh_adb_path_display()
        self.set_usb_status(f"已手动选择 ADB：{path}")
        self.set_recent(f"已手动选择 ADB：{path}")

    def set_mode(self, mode):
        self.mode_var.set(mode)
        self.mode_text.config(text=f"连接模式：{mode}")

    def run_adb(self, adb_path, args):
        return run_hidden([adb_path] + args)

    def read_adb_devices(self, adb_path):
        result = self.run_adb(adb_path, ["devices"])
        output = "\n".join(
            text for text in (result.stdout, result.stderr) if text
        ).strip()
        return result, output, parse_adb_devices(output)

    def report_adb_device_state(self, devices):
        statuses = [status for _, status in devices]
        online_devices = [d for d in devices if d[1] == "device"]
        if not devices:
            message = "未检测到 Android 设备"
        elif len(online_devices) > 1:
            message = f"检测到多个设备（{len(online_devices)} 个），请只保留一个设备后重试"
        elif "unauthorized" in statuses:
            message = "请在手机上允许 USB 调试授权"
        elif "offline" in statuses:
            message = "设备离线，请重新插拔数据线"
        else:
            message = "未检测到可用 Android 设备"

        self.set_usb_status(message)
        self.set_recent(message)
        messagebox.showwarning("PhoneType PC", message)

    def configure_usb_reverse(self):
        adb_path = self.get_adb_or_report()
        if not adb_path:
            return

        devices_result, devices_output, devices = self.read_adb_devices(adb_path)
        if devices_result.returncode != 0:
            message = devices_output or "adb devices 执行失败"
            self.set_usb_status(message)
            self.set_recent(message)
            messagebox.showerror("PhoneType PC", message)
            return

        online_devices = [d for d in devices if d[1] == "device"]
        if not online_devices:
            self.report_adb_device_state(devices)
            return

        if len(online_devices) > 1:
            self.report_adb_device_state(devices)
            return

        result = self.run_adb(adb_path, ["reverse", f"tcp:{PORT}", f"tcp:{PORT}"])
        if result.returncode != 0:
            output = "\n".join(
                text for text in (result.stdout, result.stderr) if text
            ).strip()
            message = output or "USB 转发配置失败"
            self.set_usb_status(message)
            self.set_recent(message)
            messagebox.showerror("PhoneType PC", message)
            return

        # 验证 reverse 是否生效
        reverse_result = self.run_adb(adb_path, ["reverse", "--list"])
        reverse_output = "\n".join(
            text for text in (reverse_result.stdout, reverse_result.stderr) if text
        ).strip()
        expected = f"tcp:{PORT} tcp:{PORT}"
        if expected in reverse_output:
            message = f"USB 转发已配置：tcp:{PORT} -> tcp:{PORT}"
            self.set_usb_status(message)
            self.set_recent(message)
            messagebox.showinfo("PhoneType PC", message)
        else:
            message = "USB 转发已配置，手机端请选择 USB 数据线模式并连接"
            self.set_usb_status(message)
            self.set_recent(message)

    def clear_usb_reverse(self):
        adb_path = self.get_adb_or_report()
        if not adb_path:
            return

        result = self.run_adb(adb_path, ["reverse", "--remove", f"tcp:{PORT}"])
        output = "\n".join(
            text for text in (result.stdout, result.stderr) if text
        ).strip()
        if result.returncode == 0:
            message = "USB 转发已清除"
            self.set_usb_status(message)
            self.set_recent(message)
            return

        # listener not found 不是严重错误
        if output and "not found" in output.lower():
            message = "当前没有 USB 转发需要清除"
            self.set_usb_status(message)
            self.set_recent(message)
            return

        message = output or "清除 USB 转发失败"
        self.set_usb_status(message)
        self.set_recent(message)
        messagebox.showwarning("PhoneType PC", message)

    def show_adb_devices(self):
        adb_path = self.get_adb_or_report()
        if not adb_path:
            return

        result, output, _ = self.read_adb_devices(adb_path)
        message = output or "adb devices 无输出"
        if result.returncode == 0:
            self.set_usb_status("已读取 ADB 设备列表")
            self.set_recent(message)
            messagebox.showinfo("ADB devices", message)
            return

        self.set_usb_status(message)
        self.set_recent(message)
        messagebox.showerror("ADB devices", message)

    def start_service(self):
        if self.process is not None and self.process.poll() is None:
            self.set_recent("服务已在后台监听中")
            return

        self.process = None
        if is_port_listening(PORT):
            self.set_status("端口被占用")
            self.set_recent(f"端口 {PORT} 已被占用，可能已有旧服务运行")
            self.update_buttons()
            return

        if not self.server_path.exists():
            self.set_status("未启动")
            self.set_recent("未找到 server.py")
            messagebox.showerror("PhoneType PC", f"未找到服务端文件：{self.server_path}")
            self.update_buttons()
            return

        try:
            self.process = self.create_server_process()
        except Exception as error:
            self.process = None
            self.set_status("未启动")
            self.set_recent(f"启动失败：{error}")
            messagebox.showerror("PhoneType PC", f"启动服务失败：{error}")
            self.update_buttons()
            return

        self.set_status("后台监听中")
        self.set_recent("服务已启动，后台监听中")
        threading.Thread(
            target=self.read_process_output,
            args=(self.process,),
            daemon=True,
        ).start()
        self.update_buttons()

    def create_server_process(self):
        kwargs = {
            "cwd": str(self.base_dir),
            "stdout": subprocess.PIPE,
            "stderr": subprocess.STDOUT,
            "stdin": subprocess.DEVNULL,
            "text": True,
            "encoding": "utf-8",
            "errors": "replace",
            "bufsize": 1,
        }
        if os.name == "nt":
            kwargs["creationflags"] = hidden_creationflags()

        try:
            return subprocess.Popen([sys.executable, "server.py"], **kwargs)
        except TypeError:
            kwargs.pop("encoding", None)
            kwargs.pop("errors", None)
            kwargs["universal_newlines"] = True
            return subprocess.Popen([sys.executable, "server.py"], **kwargs)

    def read_process_output(self, process):
        if process.stdout is None:
            return

        try:
            for line in process.stdout:
                text = line.strip()
                if text:
                    self.log_queue.put(text)
        except Exception as error:
            self.log_queue.put(f"读取服务输出失败：{error}")

    def stop_service(self):
        self.terminate_process()
        wait_until_port_closed(PORT, timeout=0.4)

        if is_port_listening(PORT):
            self.kill_windows_port_owner()

        if not wait_until_port_closed(PORT, timeout=1.0):
            self.set_status("端口被占用")
            self.set_recent(f"停止失败：{PORT} 仍被占用")
        else:
            self.set_status("已停止")
            self.set_recent("服务已停止")

        self.update_buttons()

    def terminate_process(self):
        if self.process is None:
            return

        process = self.process
        try:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=2)
        except Exception as error:
            self.log_queue.put(f"停止服务失败：{error}")
        finally:
            self.process = None

    def kill_windows_port_owner(self):
        if os.name != "nt":
            return

        pids = get_windows_listening_pids(PORT)
        if not pids:
            self.log_queue.put(f"未找到监听 {PORT} 的 PID")
            return

        killed = []
        for pid in pids:
            result = run_hidden(["taskkill", "/PID", pid, "/F"])
            if result.returncode == 0:
                killed.append(pid)
            else:
                detail = (result.stderr or result.stdout or "").strip()
                if detail:
                    self.log_queue.put(f"结束 PID {pid} 失败：{detail}")
                else:
                    self.log_queue.put(f"结束 PID {pid} 失败")

        if killed:
            self.log_queue.put(f"已尝试结束监听 {PORT} 的 PID：{', '.join(killed)}")

    def poll_events(self):
        while True:
            try:
                line = self.log_queue.get_nowait()
            except queue.Empty:
                break
            self.handle_server_output(line)
            self.set_recent(line)

        if self.process is not None and self.process.poll() is not None:
            self.process = None
            if is_port_listening(PORT):
                self.set_status("端口被占用")
                self.set_recent(f"server.py 已退出，但 {PORT} 仍被占用")
            else:
                self.set_status("已停止")
            self.update_buttons()

        self.root.after(200, self.poll_events)

    def update_buttons(self):
        running = self.process is not None and self.process.poll() is None
        port_busy = self.status_var.get() == "端口被占用"
        self.start_button.config(state=tk.DISABLED if running else tk.NORMAL)
        self.stop_button.config(state=tk.NORMAL if running or port_busy else tk.DISABLED)

    def on_close(self):
        self.terminate_process()
        self.root.destroy()


def main():
    root = tk.Tk()
    PhoneTypeLauncher(root)
    root.mainloop()


if __name__ == "__main__":
    main()
