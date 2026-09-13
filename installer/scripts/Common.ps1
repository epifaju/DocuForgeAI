# DocuForge AI — shared installer paths and helpers (silent / no console UI)
# Dot-source from other scripts: . "$PSScriptRoot\Common.ps1"

$ErrorActionPreference = "Stop"

$script:DocuForgeProductName = "DocuForge AI"
$script:DocuForgeComposeProject = "docuforge-ai"
$script:DocuForgeImagePrefix = "docuforge-ai-"

function Get-DocuForgeProgramData {
  $path = Join-Path $env:ProgramData "DocuForgeAI"
  if (-not (Test-Path $path)) {
    New-Item -ItemType Directory -Force -Path $path | Out-Null
  }
  return $path
}

function Get-DocuForgeInstallRoot {
  if ($env:DOCUFORGE_INSTALL_ROOT -and (Test-Path $env:DOCUFORGE_INSTALL_ROOT)) {
    return $env:DOCUFORGE_INSTALL_ROOT
  }
  $pf = Join-Path ${env:ProgramFiles} "DocuForge AI"
  if (Test-Path $pf) { return $pf }
  # Dev fallback: installer/ parent when running from source
  $dev = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
  if (Test-Path (Join-Path $dev "docker-compose.yml")) { return $dev }
  return $pf
}

function Get-DocuForgeDataRoot {
  return Get-DocuForgeProgramData
}

function Get-DocuForgeComposeDir {
  $data = Get-DocuForgeDataRoot
  $compose = Join-Path $data "compose"
  if (-not (Test-Path $compose)) {
    New-Item -ItemType Directory -Force -Path $compose | Out-Null
  }
  return $compose
}

function Get-DocuForgeLogDir {
  $logs = Join-Path (Get-DocuForgeDataRoot) "logs"
  if (-not (Test-Path $logs)) {
    New-Item -ItemType Directory -Force -Path $logs | Out-Null
  }
  return $logs
}

function Get-DocuForgeEnvPath {
  return (Join-Path (Get-DocuForgeDataRoot) ".env")
}

function Write-DocuForgeLog {
  param(
    [Parameter(Mandatory)][string]$Message,
    [ValidateSet("INFO", "WARN", "ERROR")][string]$Level = "INFO"
  )
  $logFile = Join-Path (Get-DocuForgeLogDir) ("docuforge-{0:yyyyMMdd}.log" -f (Get-Date))
  $redacted = Protect-DocuForgeSecrets $Message
  $line = "{0:yyyy-MM-dd HH:mm:ss} [{1}] {2}" -f (Get-Date), $Level, $redacted
  Add-Content -Path $logFile -Value $line -Encoding UTF8
}

function Protect-DocuForgeSecrets {
  param([string]$Text)
  if ([string]::IsNullOrEmpty($Text)) { return $Text }
  $out = $Text
  $keys = @(
    "POSTGRES_PASSWORD",
    "JWT_SECRET",
    "DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD",
    "SMTP_PASSWORD",
    "MINIO_ROOT_PASSWORD"
  )
  foreach ($k in $keys) {
    $out = [regex]::Replace($out, "(?i)($k\s*[=:]\s*)(\S+)", '${1}***')
  }
  $out = [regex]::Replace($out, "(?i)(password|secret|token)([=:]\s*)(\S+)", '${1}${2}***')
  return $out
}

function Get-DocuForgeEnvMap {
  param([string]$EnvPath = (Get-DocuForgeEnvPath))
  $map = @{}
  if (-not (Test-Path $EnvPath)) { return $map }
  Get-Content $EnvPath -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -match '^\s*#' -or $line -eq "") { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $val = $line.Substring($idx + 1).Trim()
    $map[$key] = $val
  }
  return $map
}

function Import-DocuForgeEnvToProcess {
  param([string]$EnvPath = (Get-DocuForgeEnvPath))
  $map = Get-DocuForgeEnvMap -EnvPath $EnvPath
  foreach ($k in $map.Keys) {
    Set-Item -Path "Env:$k" -Value $map[$k]
  }
}

function Test-DocuForgeDockerReady {
  try {
    $null = & docker version --format "{{.Server.Version}}" 2>$null
    if ($LASTEXITCODE -ne 0) { return $false }
    $null = & docker compose version 2>$null
    return ($LASTEXITCODE -eq 0)
  } catch {
    return $false
  }
}

function Invoke-DocuForgeCompose {
  param(
    [Parameter(Mandatory)][string[]]$ComposeArgs,
    [switch]$Capture
  )
  $composeDir = Get-DocuForgeComposeDir
  $envFile = Get-DocuForgeEnvPath
  Import-DocuForgeEnvToProcess -EnvPath $envFile

  $base = Join-Path $composeDir "docker-compose.yml"
  $selfhost = Join-Path $composeDir "docker-compose.selfhost.yml"
  if (-not (Test-Path $base)) {
    throw "Fichier compose introuvable : $base"
  }

  $args = @(
    "compose",
    "--project-name", $script:DocuForgeComposeProject,
    "-f", $base
  )
  if (Test-Path $selfhost) {
    $args += @("-f", $selfhost)
  }
  if (Test-Path $envFile) {
    $args += @("--env-file", $envFile)
  }
  $args += $ComposeArgs

  Write-DocuForgeLog "docker $($args -join ' ')"
  Push-Location $composeDir
  try {
    if ($Capture) {
      $output = & docker @args 2>&1 | Out-String
      $code = $LASTEXITCODE
      Write-DocuForgeLog $output
      return @{ ExitCode = $code; Output = $output }
    } else {
      & docker @args 2>&1 | ForEach-Object { Write-DocuForgeLog ($_ | Out-String).TrimEnd() }
      return @{ ExitCode = $LASTEXITCODE; Output = "" }
    }
  } finally {
    Pop-Location
  }
}

function Get-DocuForgeAppUrl {
  $map = Get-DocuForgeEnvMap
  $port = if ($map["FRONTEND_PORT"]) { $map["FRONTEND_PORT"] } else { "5174" }
  if ($map["APP_BASE_URL"]) { return $map["APP_BASE_URL"] }
  return "http://localhost:$port"
}

function Get-DocuForgeVersionInfoPath {
  return (Join-Path (Get-DocuForgeDataRoot) "version.json")
}
