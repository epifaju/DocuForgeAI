# DocuForge AI — generate strong local secrets into .env
# Usage: powershell -File .\scripts\secure-env.ps1 [-RotatePostgres] [-Show] [-Force] [-Prod]

param(
  [switch]$RotatePostgres,
  [switch]$Show,
  [switch]$Force,
  [switch]$Prod
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function New-Secret([int]$Bytes = 48) {
  $buf = New-Object byte[] $Bytes
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $rng.GetBytes($buf)
  } finally {
    $rng.Dispose()
  }
  return [Convert]::ToBase64String($buf).TrimEnd("=").Replace("+", "x").Replace("/", "y")
}

function New-Password([int]$Length = 24) {
  $alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%+-_"
  $bytes = New-Object byte[] $Length
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $rng.GetBytes($bytes)
  } finally {
    $rng.Dispose()
  }
  $chars = for ($i = 0; $i -lt $Length; $i++) {
    $alphabet[$bytes[$i] % $alphabet.Length]
  }
  return -join $chars
}

function Set-EnvValue([string]$Path, [string]$Key, [string]$Value) {
  $lines = Get-Content $Path
  $found = $false
  $out = foreach ($line in $lines) {
    if ($line -match ("^\s*" + [regex]::Escape($Key) + "\s*=")) {
      $found = $true
      "{0}={1}" -f $Key, $Value
    } else {
      $line
    }
  }
  if (-not $found) {
    $out += ("{0}={1}" -f $Key, $Value)
  }
  Set-Content -Path $Path -Value $out -Encoding UTF8
}

if (-not (Test-Path ".env.example")) {
  Write-Error ".env.example missing"
}

if (-not (Test-Path ".env")) {
  Copy-Item ".env.example" ".env"
  Write-Host "Created .env from .env.example"
} elseif ($Force) {
  Copy-Item ".env.example" ".env" -Force
  Write-Host "Recreated .env from .env.example (-Force)"
} else {
  Write-Host "Updating existing .env"
}

$jwt = New-Secret 48
$adminPass = New-Password 28
$pgPass = $null

Set-EnvValue ".env" "JWT_SECRET" $jwt
Set-EnvValue ".env" "DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD" $adminPass

if ($Prod -or $RotatePostgres) {
  $pgPass = New-Password 28
  Set-EnvValue ".env" "POSTGRES_PASSWORD" $pgPass
}

if ($Prod) {
  Set-EnvValue ".env" "APP_ENV" "production"
  Set-EnvValue ".env" "DOCUFORGE_BOOTSTRAP_ENABLED" "false"
  Set-EnvValue ".env" "ANTIVIRUS_ENABLED" "true"
  Write-Host "Prod flags: APP_ENV=production, bootstrap off, antivirus on"
}

Write-Host ""
Write-Host "Secrets written to .env (gitignored)."
Write-Host "----------------------------------------------"
if ($Show) {
  Write-Host ("JWT_SECRET={0}" -f $jwt)
  Write-Host ("DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD={0}" -f $adminPass)
  if ($pgPass) {
    Write-Host ("POSTGRES_PASSWORD={0}" -f $pgPass)
  }
} else {
  Write-Host "JWT_SECRET=(hidden; re-run with -Show to print once)"
  Write-Host "DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD=(hidden; re-run with -Show)"
  if ($pgPass) {
    Write-Host "POSTGRES_PASSWORD=(hidden; re-run with -Show)"
  }
}

Write-Host ""
Write-Host "Next steps:"
if ($Prod) {
  Write-Host "  1. Set DOCUFORGE_DOMAIN + APP_BASE_URL=https://... and TLS_MODE (acme|file) in .env"
  Write-Host "  2. ./scripts/verify-prod.ps1"
  Write-Host "  3. docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile antivirus --profile proxy up -d"
  Write-Host "  First install only: temporarily DOCUFORGE_BOOTSTRAP_ENABLED=true, create admin, then false + recreate."
} else {
  Write-Host "  1. docker compose up -d --force-recreate backend"
  Write-Host "  2. Bootstrap password applies only if NO users exist yet."
}
if ($pgPass) {
  Write-Host "  Postgres password changed: ALTER USER or recreate volume (docker compose down -v)."
}
Write-Host ""
Write-Host "Never commit .env. Store secrets in a password manager."
