#!/usr/bin/env python3
"""Minimal ADB client: tv.py [--screenshot out.png] 'shell cmd' | KEYNAME ..."""
import os, sys, base64, time
from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.sign_pythonrsa import PythonRSASigner
from adb_shell.auth.keygen import keygen

HOST = os.environ.get("TV_HOST", "192.168.0.109")
PORT = int(os.environ.get("TV_PORT", "5555"))
KEY = os.path.expanduser("~/.android/adbkey_tv")

def connect():
    os.makedirs(os.path.dirname(KEY), exist_ok=True)
    if not os.path.exists(KEY):
        keygen(KEY)
    signer = PythonRSASigner(open(KEY + ".pub").read(), open(KEY).read())
    d = AdbDeviceTcp(HOST, PORT, default_transport_timeout_s=30)
    d.connect(rsa_keys=[signer], auth_timeout_s=60)
    return d

def sh(d, cmd, timeout=60):
    return d.shell(cmd, timeout_s=timeout, read_timeout_s=timeout)

def screenshot(d, out):
    r = sh(d, "screencap -p /sdcard/s.png; md5sum /sdcard/s.png; wc -c < /sdcard/s.png")
    size = int(r.split()[-1])
    if size == 0:
        print("empty screenshot (protected content)"); sys.exit(1)
    data = b""
    off = 0
    while off < size:
        chunk = sh(d, f"dd if=/sdcard/s.png bs=1 skip={off} count=0 2>/dev/null; dd if=/sdcard/s.png bs=30000 skip={off//30000} count=1 2>/dev/null | base64 -w0", 120) if False else None
        break
    n = (size + 29999) // 30000
    for i in range(n):
        b = sh(d, f"dd if=/sdcard/s.png bs=30000 skip={i} count=1 2>/dev/null | base64 | tr -d '\\n'", 120)
        data += base64.b64decode(b.strip())
    open(out, "wb").write(data)
    print("saved", out, len(data), "bytes")

if __name__ == "__main__":
    a = sys.argv[1:]
    shot = None
    if a and a[0] == "--screenshot":
        shot, a = a[1], a[2:]
    d = connect()
    for x in a:
        if x.isupper() and " " not in x:
            sh(d, f"input keyevent KEYCODE_{x}"); time.sleep(0.6)
        else:
            print(sh(d, x), end="")
    if shot:
        time.sleep(0.8); screenshot(d, shot)
