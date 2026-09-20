param([Parameter(Mandatory=$true)][int]$EmulatorProcessId)
$ErrorActionPreference = 'Stop'
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $process = Get-Process -Id $EmulatorProcessId
    $process.Refresh()
    if ($process.MainWindowHandle -ne [IntPtr]::Zero) {
        [Console]::WriteLine($process.MainWindowHandle.ToInt64())
        exit 0
    }
    Start-Sleep -Milliseconds 100
}
throw 'El emulador no abrió una ventana capturable'
