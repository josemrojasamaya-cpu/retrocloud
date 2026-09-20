$adapter = Get-NetAdapter | Where-Object { $_.Status -eq "Up" -and $_.Name -eq "Wi-Fi" } | Select-Object -First 1
if (-not $adapter) { throw "No se encontró el adaptador Wi-Fi activo" }
if (-not (Get-NetIPAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object IPAddress -eq "192.168.100.112")) {
  New-NetIPAddress -InterfaceIndex $adapter.ifIndex -IPAddress "192.168.100.112" -PrefixLength 24 -SkipAsSource $true | Out-Null
}
