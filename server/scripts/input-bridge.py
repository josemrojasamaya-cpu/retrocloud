"""Persistent, process-scoped Windows keyboard bridge. No global keyboard hooks."""
import ctypes
from ctypes import wintypes as w
import json
import sys

user = ctypes.WinDLL("user32", use_last_error=True)
user.IsWindow.argtypes = [w.HWND]
user.PostMessageW.argtypes = [w.HWND, w.UINT, w.WPARAM, w.LPARAM]
user.PostMessageW.restype = w.BOOL
user.MapVirtualKeyW.argtypes = [w.UINT, w.UINT]
user.MapVirtualKeyW.restype = w.UINT
target = int(sys.argv[1])
held = set()
allowed = {0x58, 0x5A, 0x41, 0x53, 0x0D, 0x08, 0x25, 0x26, 0x27, 0x28}

def post(key, down):
    if not user.IsWindow(target):
        raise RuntimeError("La ventana del emulador se cerró")
    param = 1 | (user.MapVirtualKeyW(key, 0) << 16)
    if key in (0x25, 0x26, 0x27, 0x28):
        param |= 1 << 24
    if not down:
        param |= 3 << 30
    if not user.PostMessageW(target, 0x100 if down else 0x101, key, param):
        raise ctypes.WinError(ctypes.get_last_error())

try:
    for line in sys.stdin:
        request = {}
        try:
            request = json.loads(line)
            desired = set(request["keys"])
            if not desired.issubset(allowed):
                raise ValueError("Tecla no permitida")
            for key in held - desired:
                post(key, False)
            for key in desired - held:
                post(key, True)
            held = desired
            print(json.dumps({"id": request["id"], "ok": True}), flush=True)
        except Exception as exc:
            print(json.dumps({"id": request.get("id"), "ok": False, "error": str(exc)}), flush=True)
finally:
    for key in held:
        try:
            post(key, False)
        except Exception:
            pass
