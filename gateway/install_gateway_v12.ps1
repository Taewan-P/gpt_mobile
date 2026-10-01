param(
    [string]$Destination = 'D:\MCP\gateway\gateway.py',
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$Source = Join-Path $PSScriptRoot 'gateway_v12.py'
if (!(Test-Path $Source)) { throw "Missing source: $Source" }
& $Python -m py_compile $Source
if ($LASTEXITCODE -ne 0) { throw 'Python syntax validation failed; installation stopped.' }
if (!(Select-String -Path $Source -SimpleMatch 'GATEWAY_VERSION = "12.0.0"')) {
    throw 'Source does not identify itself as gateway v12.'
}
$Parent = Split-Path -Parent $Destination
New-Item -ItemType Directory -Force -Path $Parent | Out-Null
if (Test-Path $Destination) {
    $Backup = "$Destination.$(Get-Date -Format 'yyyyMMdd-HHmmss-fff').bak"
    Copy-Item -LiteralPath $Destination -Destination $Backup
    Write-Host "Previous gateway saved to $Backup"
}
Copy-Item -LiteralPath $Source -Destination "$Destination.new"
Move-Item -LiteralPath "$Destination.new" -Destination $Destination -Force
Write-Host "Gateway v12 installed at $Destination. Restart your gateway process to activate it."
