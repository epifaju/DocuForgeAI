# DocuForge AI — generate .env from wizard answers (no secrets in console output)
# Usage:
#   powershell -File New-DocuForgeEnv.ps1 -CompanyName "Consulat" -AdminEmail "admin@example.gov" `
#     -AdminPassword "***" -FrontendPort 5174 [-EnableAi]

param(
  [Parameter(Mandatory)][string]$CompanyName,
  [Parameter(Mandatory)][string]$AdminEmail,
  [Parameter(Mandatory)][string]$AdminPassword,
  [int]$FrontendPort = 5174,
  [switch]$EnableAi,
  [string]$CompanyIdentifier = "",
  [string]$EnvExamplePath = "",
  [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

function New-Secret([int]$Bytes = 48) {
  $buf = New-Object byte[] $Bytes
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($buf) } finally { $rng.Dispose() }
  return [Convert]::ToBase64String($buf).TrimEnd("=").Replace("+", "x").Replace("/", "y")
}

function New-Password([int]$Length = 28) {
  $alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%+-_"
  $bytes = New-Object byte[] $Length
  $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
  $chars = for ($i = 0; $i -lt $Length; $i++) { $alphabet[$bytes[$i] % $alphabet.Length] }
  return -join $chars
}

function Get-Slug([string]$Name) {
  $s = $Name.ToLowerInvariant()
  $s = [regex]::Replace($s, "[^a-z0-9]+", "-")
  $s = $s.Trim("-")
  if ([string]::IsNullOrWhiteSpace($s)) { $s = "org" }
  if ($s.Length -gt 32) { $s = $s.Substring(0, 32).TrimEnd("-") }
  return $s
}

function Set-EnvValue([string]$Path, [string]$Key, [string]$Value) {
  $lines = @(Get-Content $Path -Encoding UTF8)
  $found = $false
  $out = foreach ($line in $lines) {
    if ($line -match ("^\s*" + [regex]::Escape($Key) + "\s*=")) {
      $found = $true
      "{0}={1}" -f $Key, $Value
    } else { $line }
  }
  if (-not $found) { $out += ("{0}={1}" -f $Key, $Value) }
  Set-Content -Path $Path -Value $out -Encoding UTF8
}

$dataRoot = Get-DocuForgeDataRoot
if (-not $OutputPath) { $OutputPath = Get-DocuForgeEnvPath }
if (-not $EnvExamplePath) {
  $candidates = @(
    (Join-Path (Get-DocuForgeComposeDir) ".env.example"),
    (Join-Path (Get-DocuForgeInstallRoot) ".env.example"),
    (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) ".env.example")
  )
  foreach ($c in $candidates) {
    if (Test-Path $c) { $EnvExamplePath = $c; break }
  }
}
if (-not (Test-Path $EnvExamplePath)) {
  throw ".env.example introuvable"
}

Copy-Item $EnvExamplePath $OutputPath -Force

if ([string]::IsNullOrWhiteSpace($CompanyIdentifier)) {
  $CompanyIdentifier = Get-Slug $CompanyName
}

$jwt = New-Secret 48
$pgPass = New-Password 28
$appUrl = "http://localhost:$FrontendPort"

Set-EnvValue $OutputPath "APP_ENV" "production"
Set-EnvValue $OutputPath "APP_BASE_URL" $appUrl
Set-EnvValue $OutputPath "FRONTEND_PORT" "$FrontendPort"
Set-EnvValue $OutputPath "JWT_SECRET" $jwt
Set-EnvValue $OutputPath "POSTGRES_PASSWORD" $pgPass
Set-EnvValue $OutputPath "STORAGE_VOLUME" "storage_data"
Set-EnvValue $OutputPath "DOCUFORGE_BOOTSTRAP_ENABLED" "true"
Set-EnvValue $OutputPath "DOCUFORGE_BOOTSTRAP_COMPANY_NAME" $CompanyName
Set-EnvValue $OutputPath "DOCUFORGE_BOOTSTRAP_COMPANY_IDENTIFIER" $CompanyIdentifier
Set-EnvValue $OutputPath "DOCUFORGE_BOOTSTRAP_ADMIN_EMAIL" $AdminEmail
Set-EnvValue $OutputPath "DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD" $AdminPassword
Set-EnvValue $OutputPath "AUTH_COOKIE_SECURE" "false"

if ($EnableAi) {
  Set-EnvValue $OutputPath "AI_ENABLED" "true"
  Set-EnvValue $OutputPath "COMPOSE_PROFILES" "ai"
  Set-EnvValue $OutputPath "AI_PROVIDER" "ollama"
} else {
  Set-EnvValue $OutputPath "AI_ENABLED" "false"
  Set-EnvValue $OutputPath "COMPOSE_PROFILES" ""
}

# Restrict ACL to Administrators + SYSTEM + current user
try {
  $acl = Get-Acl $OutputPath
  $acl.SetAccessRuleProtection($true, $false)
  $rules = @(
    (New-Object System.Security.AccessControl.FileSystemAccessRule("SYSTEM", "FullControl", "Allow")),
    (New-Object System.Security.AccessControl.FileSystemAccessRule("Administrators", "FullControl", "Allow")),
    (New-Object System.Security.AccessControl.FileSystemAccessRule([System.Security.Principal.WindowsIdentity]::GetCurrent().Name, "FullControl", "Allow"))
  )
  foreach ($r in $rules) { $acl.AddAccessRule($r) }
  Set-Acl -Path $OutputPath -AclObject $acl
} catch {
  Write-DocuForgeLog "ACL restrict failed: $($_.Exception.Message)" "WARN"
}

Write-DocuForgeLog "Generated .env for company=$CompanyIdentifier port=$FrontendPort ai=$EnableAi"

$versionPath = Get-DocuForgeVersionInfoPath
@{
  version = "0.1.0"
  channel = "stable"
  installedAt = (Get-Date).ToString("o")
  company = $CompanyIdentifier
} | ConvertTo-Json | Set-Content -Path $versionPath -Encoding UTF8

# Machine-readable success for Inno (no secrets)
Write-Output "OK|$OutputPath|$CompanyIdentifier|$FrontendPort"
exit 0
