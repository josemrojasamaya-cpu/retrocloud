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
platform = sys.argv[2] if len(sys.argv) > 2 else 'gba'
held = set()
allowed = {0x58, 0x5A, 0x41, 0x53, 0x0D, 0x08, 0x25, 0x26, 0x27, 0x28}
if platform == 'ds':
    allowed |= {0x57, 0x51}
elif platform == 'ps1':
    allowed |= {0x57, 0x51, 0x44, 0x43}
elif platform == 'psp':
    allowed |= {0x57, 0x51}
user.GetClientRect.argtypes = [w.HWND, ctypes.POINTER(w.RECT)]
touching = False
last_touch = 0

def touch(event):
    global touching, last_touch
    if platform != 'ds' or not user.IsWindow(target):
        raise RuntimeError('Panel DS no disponible')
    phase, x, y = event['phase'], event['x'], event['y']
    if phase not in ('down', 'move', 'up') or not 0 <= x <= 1 or not 0 <= y <= 1:
        raise ValueError('Toque inválido')
    rect = w.RECT()
    if not user.GetClientRect(target, ctypes.byref(rect)):
        raise ctypes.WinError(ctypes.get_last_error())
    # Controlled melonDS layout: fullscreen, equal screens, horizontal, no gap.
    # The lower DS screen is the right half of the 512x192 letterboxed canvas.
    width, height = rect.right, rect.bottom
    scale = min(width / 512, height / 192)
    px = round((width - 512 * scale) / 2 + (256 + x * 255) * scale)
    py = round((height - 192 * scale) / 2 + y * 191 * scale)
    last_touch = (py << 16) | px
    message = {'down': 0x201, 'move': 0x200, 'up': 0x202}[phase]
    if not user.PostMessageW(target, message, 0 if phase == 'up' else 1, last_touch):
        raise ctypes.WinError(ctypes.get_last_error())
    touching = phase != 'up'

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
            if request.get('touch'):
                touch(request['touch'])
            desired = set(request.get("keys", held))
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
    if touching:
        user.PostMessageW(target, 0x202, 0, last_touch)
    for key in held:
        try:
            post(key, False)
        except Exception:
            pass
