# Build offline image pack (ops): docker save -> installer/payload/images
param(
  [string]$OutputDir = "",
  [string[]]$Images = @(
    "docuforge-ai-backend:0.1.0",
    "docuforge-ai-frontend:0.1.0",
    "docuforge-ai-libreoffice:0.1.0",
    "postgres:16.6-alpine",
    "axllent/mailpit:v1.22.3"
  )
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

if (-not $OutputDir) {
  $OutputDir = Join-Path (Split-Path -Parent $PSScriptRoot) "payload\images"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

foreach ($img in $Images) {
  $safe = ($img -replace "[/\\:]", "_") + ".tar"
  $out = Join-Path $OutputDir $safe
  Write-Host "Saving $img -> $out"
  & docker save -o $out $img
  if ($LASTEXITCODE -ne 0) {
    Write-Warning "Failed to save $img (image may be missing - build first)"
  }
}

Write-Host "Done. Pack folder: $OutputDir"
