import os
import queue
import socket
import subprocess
import sys
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import messagebox


PORT = 8765
LOCAL_CHECK_HOST = "127.0.0.1"


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

        self.root.title("PhoneType PC")
        self.root.geometry("420x240")
        self.root.resizable(False, False)
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

        self.status_var = tk.StringVar(value="未启动")
        self.address_var = tk.StringVar(value=f"{get_lan_ip()}:{PORT}")
        self.recent_var = tk.StringVar(value="等待手机连接")

        self.build_ui()
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

        self.recent_text = tk.Label(
            container,
            text=f"最近状态：{self.recent_var.get()}",
            font=("Microsoft YaHei UI", 10),
            anchor="w",
            justify="left",
            wraplength=360,
        )
        self.recent_text.pack(anchor="w", fill=tk.X, pady=(8, 0))

        button_row = tk.Frame(container)
        button_row.pack(anchor="w", pady=(20, 0))

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
