# Prepare installer/payload/compose from repo root (run before ISCC)
param(
  [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) {
  $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}

$dest = Join-Path (Split-Path -Parent $PSScriptRoot) "payload\compose"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$files = @(
  "docker-compose.yml",
  "docker-compose.selfhost.yml",
  "docker-compose.prod.yml",
  ".env.example"
)
foreach ($f in $files) {
  $src = Join-Path $RepoRoot $f
  if (-not (Test-Path $src)) { throw "Missing $src" }
  Copy-Item $src (Join-Path $dest $f) -Force
}

$pgSrc = Join-Path $RepoRoot "infrastructure\postgres\init"
$pgDst = Join-Path $dest "infrastructure\postgres\init"
if (Test-Path $pgSrc) {
  New-Item -ItemType Directory -Force -Path $pgDst | Out-Null
  Copy-Item (Join-Path $pgSrc "*") $pgDst -Recurse -Force
}

# Placeholder dirs
New-Item -ItemType Directory -Force -Path (Join-Path (Split-Path -Parent $PSScriptRoot) "payload\images") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path (Split-Path -Parent $PSScriptRoot) "payload\prereqs") | Out-Null
"# Place RancherDesktopSetup.exe here for offline prereq install" |
  Set-Content (Join-Path (Split-Path -Parent $PSScriptRoot) "payload\prereqs\README.txt") -Encoding UTF8
"# Place docker save *.tar files here (see Export-OfflineImages.ps1)" |
  Set-Content (Join-Path (Split-Path -Parent $PSScriptRoot) "payload\images\README.txt") -Encoding UTF8

Write-Host "Payload ready: $dest"
