# Copy compose files into ProgramData for the installed product
param(
  [string]$SourceRoot = "",
  [string]$DestDir = ""
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

if (-not $SourceRoot) {
  $SourceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}
if (-not $DestDir) {
  $DestDir = Get-DocuForgeComposeDir
}

New-Item -ItemType Directory -Force -Path $DestDir | Out-Null

$files = @(
  "docker-compose.yml",
  "docker-compose.selfhost.yml",
  "docker-compose.prod.yml",
  ".env.example"
)
foreach ($f in $files) {
  $src = Join-Path $SourceRoot $f
  if (Test-Path $src) {
    Copy-Item $src (Join-Path $DestDir $f) -Force
  }
}

# Copy libreoffice build context reference is not needed if images are prebuilt;
# keep infrastructure init for postgres if present
$pgInitSrc = Join-Path $SourceRoot "infrastructure\postgres\init"
$pgInitDst = Join-Path $DestDir "infrastructure\postgres\init"
if (Test-Path $pgInitSrc) {
  New-Item -ItemType Directory -Force -Path $pgInitDst | Out-Null
  Copy-Item (Join-Path $pgInitSrc "*") $pgInitDst -Recurse -Force
}

# Patch compose relative init path if we flattened — keep same relative structure under compose dir
$version = @{ version = "0.1.0"; channel = "stable"; installedAt = (Get-Date).ToString("o") }
$version | ConvertTo-Json | Set-Content -Path (Get-DocuForgeVersionInfoPath) -Encoding UTF8

Write-Output "OK|$DestDir"
exit 0
