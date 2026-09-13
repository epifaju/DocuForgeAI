# Load offline docker images from a directory of *.tar / *.tar.gz
param(
  [string]$ImagesDir = ""
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

if (-not $ImagesDir) {
  $ImagesDir = Join-Path (Get-DocuForgeDataRoot) "images"
}
if (-not (Test-Path $ImagesDir)) {
  Write-DocuForgeLog "No offline images dir: $ImagesDir"
  Write-Output "OK|NO_IMAGES"
  exit 0
}

if (-not (Test-DocuForgeDockerReady)) {
  Write-Output "ERROR|DOCKER_NOT_READY"
  exit 2
}

$files = @(Get-ChildItem -Path $ImagesDir -File -Include *.tar,*.tar.gz,*.img | Sort-Object Name)
if ($files.Count -eq 0) {
  # Also search zip extracts
  $files = @(Get-ChildItem -Path $ImagesDir -Recurse -File -Include *.tar,*.tar.gz | Sort-Object Name)
}

$loaded = 0
foreach ($f in $files) {
  Write-DocuForgeLog "docker load -i $($f.FullName)"
  & docker load -i $f.FullName 2>&1 | ForEach-Object { Write-DocuForgeLog ($_ | Out-String).TrimEnd() }
  if ($LASTEXITCODE -eq 0) { $loaded++ }
}

Write-Output "OK|LOADED|$loaded"
exit 0
