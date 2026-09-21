param([Parameter(Mandatory=$true)][int]$EmulatorProcessId)
$ErrorActionPreference = 'Stop'

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class WinApi {
    [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr hWnd, int X, int Y, int nWidth, int nHeight, bool bRepaint);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
    [DllImport("user32.dll")] public static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);
    [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr hWnd, int nIndex);
}
"@

Add-Type -AssemblyName System.Windows.Forms

$HWND_BOTTOM = [IntPtr]::new(1)
# SWP_NOACTIVATE | SWP_SHOWWINDOW
$SWP_FLAGS = 0x0010 -bor 0x0040

for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $process = Get-Process -Id $EmulatorProcessId
    $process.Refresh()
    if ($process.MainWindowHandle -ne [IntPtr]::Zero) {
        $hwnd = $process.MainWindowHandle
        $screen = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
        $w = 512; $h = 384
        $x = $screen.Right - $w - 10
        $y = $screen.Bottom - $h - 10
        # Position at bottom-right, behind all windows, without activating
        [WinApi]::SetWindowPos($hwnd, $HWND_BOTTOM, $x, $y, $w, $h, $SWP_FLAGS) | Out-Null
        [Console]::WriteLine($hwnd.ToInt64())
        exit 0
    }
    Start-Sleep -Milliseconds 100
}
throw 'El emulador no abrió una ventana capturable'
