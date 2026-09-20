param(
  [Parameter(Mandatory = $true)][string]$BindAddress,
  [Parameter(Mandatory = $true)][string]$SessionToken,
  [string]$Port = "8080",
  [string]$MgbaExecutable = "C:\Program Files\mGBA\mGBA.exe",
  [string]$MelondsExecutable = ""
)

$env:LOCAL_PC_BIND_ADDRESS = $BindAddress
$env:PORT = $Port
$env:SESSION_API_TOKEN = $SessionToken
$env:MGBA_EXECUTABLE = $MgbaExecutable
$env:MELONDS_EXECUTABLE = $MelondsExecutable
node "$PSScriptRoot\..\src\local-pc.mjs"
