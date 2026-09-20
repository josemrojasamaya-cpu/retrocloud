param([int]$ProcessId, [string]$Key, [string]$Down)
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class RetroKeys {
 [DllImport("user32.dll")] public static extern IntPtr FindWindow(string c, string title);
 [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
}
'@
$map = @{ Z=0x5A; X=0x58; A=0x41; S=0x53; ENTER=0x0D; BACK=0x08; UP=0x26; DOWN=0x28; LEFT=0x25; RIGHT=0x27 }
$window = (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue).MainWindowHandle
if ($window -eq [IntPtr]::Zero -or -not $map.ContainsKey($Key)) { exit 1 }
$message = if ($Down -eq 'True') { 0x0100 } else { 0x0101 }
if (-not [RetroKeys]::PostMessage($window, $message, [IntPtr]$map[$Key], [IntPtr]::Zero)) { exit 1 }
