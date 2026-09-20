param(
  [string]$BindAddress = "",
  [string]$SessionToken = "amayomi2024",
  [string]$Port = "8080",
  [string]$MgbaExecutable = "C:\Program Files\mGBA\mGBA.exe",
  [string]$MelondsExecutable = "",
  [string]$FfmpegExecutable = ""
)

# Auto-detect local IP if not provided
if (-not $BindAddress) {
  $ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike '*Loopback*' -and $_.PrefixOrigin -eq 'Dhcp' } | Select-Object -First 1).IPAddress
  if (-not $ip) { $ip = "127.0.0.1" }
  $BindAddress = $ip
}

# Auto-detect FFmpeg
if (-not $FfmpegExecutable) {
  $ffmpegPath = (Get-Command ffmpeg -ErrorAction SilentlyContinue).Source
  if ($ffmpegPath) { $FfmpegExecutable = $ffmpegPath }
  else { Write-Host "ADVERTENCIA: FFmpeg no encontrado. El streaming de video no funcionara." -ForegroundColor Yellow }
}

$env:LOCAL_PC_BIND_ADDRESS = $BindAddress
$env:PORT = $Port
$env:SESSION_API_TOKEN = $SessionToken
$env:MGBA_EXECUTABLE = $MgbaExecutable
$env:MELONDS_EXECUTABLE = $MelondsExecutable
$env:FFMPEG_EXECUTABLE = $FfmpegExecutable

Write-Host ""
Write-Host "  ========================================" -ForegroundColor Cyan
Write-Host "    AMAYOMI RETRO - Servidor Local PC" -ForegroundColor Cyan
Write-Host "  ========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  IP del servidor:  $BindAddress" -ForegroundColor Green
Write-Host "  Puerto:           $Port" -ForegroundColor Green
Write-Host "  mGBA:             $MgbaExecutable" -ForegroundColor Green
Write-Host "  FFmpeg:           $FfmpegExecutable" -ForegroundColor Green
Write-Host ""
Write-Host "  Configura la APK con:" -ForegroundColor Yellow
Write-Host "    RETROSALA_SERVER_MODE = local_pc" -ForegroundColor Yellow
Write-Host "    RETROSALA_API_URL = http://${BindAddress}:${Port}" -ForegroundColor Yellow
Write-Host "    RETROSALA_SESSION_TOKEN = $SessionToken" -ForegroundColor Yellow
Write-Host ""

node "$PSScriptRoot\..\src\local-pc.mjs"
