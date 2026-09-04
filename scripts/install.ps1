# DocuForge AI — install / first start (Phase 21)
# Usage: powershell -File .\scripts\install.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "DocuForge AI — installation"
Write-Host "---------------------------"

if (-not (Test-Path ".env")) {
  Copy-Item ".env.example" ".env"
  Write-Host "Created .env from .env.example — change changeme_* secrets before production."
} else {
  Write-Host ".env already present — left unchanged."
}

if (Test-Path ".env") {
  Get-Content ".env" | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $parts = $_.Split("=", 2)
    if ($parts.Length -eq 2) {
      Set-Item -Path "Env:$($parts[0].Trim())" -Value $parts[1].Trim()
    }
  }
}

$backendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { "18081" }
$frontendPort = if ($env:FRONTEND_PORT) { $env:FRONTEND_PORT } else { "5174" }
$mailpitPort = if ($env:MAILPIT_UI_PORT) { $env:MAILPIT_UI_PORT } else { "8028" }

Write-Host "Building images…"
docker compose build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting stack…"
docker compose up -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Waiting for backend health…"
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
  try {
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:$backendPort/actuator/health" -UseBasicParsing -TimeoutSec 3
    if ($r.StatusCode -eq 200) { $ok = $true; break }
  } catch {
    Start-Sleep -Seconds 2
  }
}
if (-not $ok) {
  Write-Host "ERROR: backend not healthy after ~2 minutes."
  docker compose ps
  exit 1
}

if (Test-Path ".\scripts\healthcheck.ps1") {
  powershell -NoProfile -File ".\scripts\healthcheck.ps1"
}

Write-Host "---------------------------"
Write-Host "Installation ready."
Write-Host "  UI:        http://localhost:$frontendPort"
Write-Host "  API:       http://localhost:$backendPort"
Write-Host "  Mailpit:   http://localhost:$mailpitPort"
Write-Host "  Login:     company=demo / admin@demo.local / (see .env DOCUFORGE_BOOTSTRAP_*)"
Write-Host "  Docs:      docs/installation.md"
Write-Host "  Templates: templates/demo/"
