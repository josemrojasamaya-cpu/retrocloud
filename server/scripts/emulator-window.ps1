param(
  [Parameter(Mandatory=$true)][int]$EmulatorProcessId,
  [string]$Platform = "gba"
)
$ErrorActionPreference = 'Stop'

Add-Type @"
using System;
using System.Runtime.InteropServices;
public struct POINT { public int X, Y; }
public struct RECT { public int Left, Top, Right, Bottom; }
public class WinApi {
    [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr hWnd, int X, int Y, int nWidth, int nHeight, bool bRepaint);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
    [DllImport("user32.dll")] public static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);
    [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr hWnd, int nIndex);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT p);
}
"@

Add-Type -AssemblyName System.Windows.Forms

$HWND_BOTTOM = [IntPtr]::new(1)
$HWND_TOPMOST = [IntPtr]::new(-1)
# SWP_NOACTIVATE | SWP_SHOWWINDOW
$SWP_FLAGS = 0x0010 -bor 0x0040
# GPU-rendered emulators are captured from the desktop, so their window must
# stay visible and unobstructed instead of being pushed behind everything.
$gpuPlatforms = @('ps1', 'psp')

for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $process = Get-Process -Id $EmulatorProcessId
    $process.Refresh()
    if ($process.MainWindowHandle -ne [IntPtr]::Zero) {
        $hwnd = $process.MainWindowHandle
        $screen = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
        $w = 512; $h = 384
        $x = $screen.Right - $w - 10
        $y = $screen.Bottom - $h - 10

        if ($gpuPlatforms -contains $Platform) {
            [WinApi]::SetWindowPos($hwnd, $HWND_TOPMOST, $x, $y, $w, $h, $SWP_FLAGS) | Out-Null
            Start-Sleep -Milliseconds 400
            # Report the client area in screen coordinates so the capture skips
            # the title bar and borders.
            $cr = New-Object RECT
            [WinApi]::GetClientRect($hwnd, [ref]$cr) | Out-Null
            $origin = New-Object POINT
            [WinApi]::ClientToScreen($hwnd, [ref]$origin) | Out-Null
            $cw = $cr.Right - $cr.Left
            $ch = $cr.Bottom - $cr.Top
            if ($cw % 2 -ne 0) { $cw-- }
            if ($ch % 2 -ne 0) { $ch-- }
            [Console]::WriteLine("$($hwnd.ToInt64()) $($origin.X) $($origin.Y) $cw $ch")
        } else {
            # Position at bottom-right, behind all windows, without activating
            [WinApi]::SetWindowPos($hwnd, $HWND_BOTTOM, $x, $y, $w, $h, $SWP_FLAGS) | Out-Null
            [Console]::WriteLine("$($hwnd.ToInt64()) $x $y $w $h")
        }
        exit 0
    }
    Start-Sleep -Milliseconds 100
}
throw 'El emulador no abrió una ventana capturable'
