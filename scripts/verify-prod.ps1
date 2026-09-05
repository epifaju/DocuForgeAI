# DocuForge AI — verify .env readiness for production-like deploy (U0)
# Usage: powershell -File .\scripts\verify-prod.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path ".env")) {
  Write-Error ".env missing — copy .env.example and run scripts/secure-env.ps1 -Prod"
}

function Get-DotEnv([string]$Path) {
  $map = @{}
  Get-Content $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $i = $line.IndexOf("=")
    if ($i -lt 1) { return }
    $k = $line.Substring(0, $i).Trim()
    $v = $line.Substring($i + 1).Trim()
    $map[$k] = $v
  }
  return $map
}

$envMap = Get-DotEnv ".env"
$fail = 0
function Ok([string]$m) { Write-Host "OK: $m" }
function Warn([string]$m) { Write-Host "WARN: $m" }
function Die([string]$m) { Write-Host "FAIL: $m"; $script:fail++ }

$appEnv = $envMap["APP_ENV"]
if ($appEnv -eq "production" -or $appEnv -eq "prod") { Ok "APP_ENV=$appEnv" }
else { Die "APP_ENV must be production (got '$appEnv')" }

$jwt = $envMap["JWT_SECRET"]
if (-not $jwt -or $jwt.Length -lt 32) { Die "JWT_SECRET too short (<32)" }
elseif ($jwt -match "(?i)^changeme") { Die "JWT_SECRET is still a placeholder" }
else { Ok "JWT_SECRET length=$($jwt.Length)" }

$pg = $envMap["POSTGRES_PASSWORD"]
if (-not $pg) { Die "POSTGRES_PASSWORD missing" }
elseif ($pg -match "(?i)^changeme") { Die "POSTGRES_PASSWORD is still a placeholder" }
else { Ok "POSTGRES_PASSWORD set" }

$boot = $envMap["DOCUFORGE_BOOTSTRAP_ENABLED"]
if ($boot -eq "false" -or $boot -eq "0") { Ok "bootstrap disabled" }
else { Warn "DOCUFORGE_BOOTSTRAP_ENABLED=$boot — must be false after first admin" }

$av = $envMap["ANTIVIRUS_ENABLED"]
if ($av -eq "true" -or $av -eq "1") { Ok "antivirus enabled" }
else { Warn "ANTIVIRUS_ENABLED is not true — enable for production uploads" }

$domain = $envMap["DOCUFORGE_DOMAIN"]
if ([string]::IsNullOrWhiteSpace($domain)) { Warn "DOCUFORGE_DOMAIN unset (needed for Traefik Host rule)" }
else { Ok "DOCUFORGE_DOMAIN=$domain" }

$base = $envMap["APP_BASE_URL"]
if ($base -like "https://*") { Ok "APP_BASE_URL=$base" }
else { Warn "APP_BASE_URL should be https://... (got '$base')" }

$tlsMode = if ($envMap["TLS_MODE"]) { $envMap["TLS_MODE"] } else { "acme" }
switch ($tlsMode) {
  "acme" {
    Ok "TLS_MODE=acme"
    $email = $envMap["ACME_EMAIL"]
    if ([string]::IsNullOrWhiteSpace($email) -or $email -eq "admin@example.com") {
      Warn "Set ACME_EMAIL to a real address for Let's Encrypt"
    } else { Ok "ACME_EMAIL set" }
  }
  "file" {
    Ok "TLS_MODE=file"
    if (-not (Test-Path "certs/fullchain.pem") -or -not (Test-Path "certs/privkey.pem")) {
      Die "TLS_MODE=file requires certs/fullchain.pem and certs/privkey.pem"
    } else { Ok "certs present" }
    if (-not (Test-Path "infrastructure/traefik/dynamic/tls.yml")) {
      Die "Copy infrastructure/traefik/dynamic/tls-file.yml.example to tls.yml"
    } else { Ok "traefik dynamic tls.yml present" }
  }
  default { Die "TLS_MODE must be acme or file (got '$tlsMode')" }
}

Write-Host ""
if ($fail -gt 0) {
  Write-Host "verify-prod: FAILED"
  exit 1
}
Write-Host "verify-prod: PASSED"
Write-Host "Suggested up:"
Write-Host "  docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.antivirus.yml --profile antivirus --profile proxy up -d"
if ($tlsMode -eq "file") {
  Write-Host "  (add -f docker-compose.tls-file.yml for file certificates)"
}
