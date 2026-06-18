import json
import socket
import socketserver
import threading

import pyperclip
from pynput.keyboard import Controller, Key


HOST = "0.0.0.0"
PORT = 8765
SERVER_VERSION = "v1.0-final"

paste_lock = threading.Lock()
keyboard = Controller()

KEY_MAP = {
    "backspace": Key.backspace,
    "delete": Key.delete,
    "enter": Key.enter,
    "tab": Key.tab,
    "esc": Key.esc,
    "left": Key.left,
    "right": Key.right,
    "up": Key.up,
    "down": Key.down,
}

SHORTCUT_KEY_MAP = {
    "ctrl": Key.ctrl,
    "shift": Key.shift,
    "alt": Key.alt,
    "win": Key.cmd,
    "enter": Key.enter,
    "a": "a",
    "c": "c",
    "x": "x",
    "v": "v",
    "z": "z",
    "s": "s",
}

SUPPORTED_SHORTCUTS = {
    ("ctrl", "a"),
    ("ctrl", "c"),
    ("ctrl", "x"),
    ("ctrl", "v"),
    ("ctrl", "z"),
    ("shift", "enter"),
    ("win", "shift", "s"),
}


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


def send_response(wfile, ok, message):
    payload = json.dumps({"ok": ok, "message": message}, ensure_ascii=False)
    wfile.write((payload + "\n").encode("utf-8"))
    wfile.flush()


def paste_text(text, enter):
    with paste_lock:
        pyperclip.copy(text)
        keyboard.press(Key.ctrl)
        keyboard.press("v")
        keyboard.release("v")
        keyboard.release(Key.ctrl)

        if enter:
            keyboard.press(Key.enter)
            keyboard.release(Key.enter)


def press_single_key(key_name):
    key = KEY_MAP.get(key_name)
    if key is None:
        return False

    with paste_lock:
        keyboard.press(key)
        keyboard.release(key)

    return True


def press_shortcut(keys):
    key_tuple = tuple(keys)
    if key_tuple not in SUPPORTED_SHORTCUTS:
        return False

    resolved_keys = [SHORTCUT_KEY_MAP[key_name] for key_name in key_tuple]

    with paste_lock:
        for key in resolved_keys:
            keyboard.press(key)
        for key in reversed(resolved_keys):
            keyboard.release(key)

    return True


def format_shortcut(keys):
    if isinstance(keys, list):
        return "+".join(str(key) for key in keys)
    return str(keys)


class PhoneTypeRequestHandler(socketserver.StreamRequestHandler):
    def handle(self):
        for raw_line in self.rfile:
            try:
                line = raw_line.decode("utf-8").strip()
                if not line:
                    send_response(self.wfile, False, "empty message")
                    continue

                data = json.loads(line)
                message_type = data.get("type")

                if message_type == "ping":
                    send_response(self.wfile, True, "pong")
                    continue

                if message_type == "key":
                    key_name = str(data.get("key", "")).lower()
                    if not press_single_key(key_name):
                        send_response(self.wfile, False, f"unsupported key: {key_name}")
                        continue

                    send_response(self.wfile, True, f"key:{key_name}")
                    continue

                if message_type == "shortcut":
                    raw_keys = data.get("keys")
                    keys = [
                        key.lower()
                        for key in raw_keys
                        if isinstance(key, str)
                    ] if isinstance(raw_keys, list) else []
                    shortcut_name = format_shortcut(keys if keys else raw_keys)

                    if not keys or len(keys) != len(raw_keys) or not press_shortcut(keys):
                        send_response(self.wfile, False, f"unsupported shortcut: {shortcut_name}")
                        continue

                    send_response(self.wfile, True, f"shortcut:{shortcut_name}")
                    continue

                if message_type != "text":
                    send_response(self.wfile, False, "unsupported message type")
                    continue

                text = data.get("text")
                if not isinstance(text, str):
                    send_response(self.wfile, False, "text must be a string")
                    continue

                enter = bool(data.get("enter", False))
                paste_text(text, enter)
                send_response(self.wfile, True, "inserted")
            except Exception as error:
                send_response(self.wfile, False, str(error) or error.__class__.__name__)


class ThreadingTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    print(f"PhoneTypePC version: {SERVER_VERSION}", flush=True)
    print(f"PhoneTypePC TCP server listening on {HOST}:{PORT}", flush=True)
    print(f"LAN address: {get_lan_ip()}:{PORT}", flush=True)
    print("Protocol: JSON Lines, one JSON object per line.", flush=True)

    with ThreadingTCPServer((HOST, PORT), PhoneTypeRequestHandler) as server:
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nPhoneTypePC TCP server stopped.", flush=True)


if __name__ == "__main__":
    main()
