# DocuForge AI — start stack (hidden-friendly)
param(
  [switch]$Build,
  [switch]$WaitHealthy,
  [int]$TimeoutSeconds = 300,
  [string]$ImagesDir = ""
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

if (-not (Test-DocuForgeDockerReady)) {
  Write-DocuForgeLog "Docker engine not ready" "ERROR"
  Write-Output "ERROR|DOCKER_NOT_READY"
  exit 2
}

$map = Get-DocuForgeEnvMap
$enableAi = ($map["AI_ENABLED"] -eq "true") -or (($map["COMPOSE_PROFILES"] -as [string]) -match "\bai\b")

# Load offline images if present
if (-not $ImagesDir) {
  $ImagesDir = Join-Path (Get-DocuForgeDataRoot) "images"
}
$loadScript = Join-Path $PSScriptRoot "Load-OfflineImages.ps1"
if ((Test-Path $loadScript) -and (Test-Path $ImagesDir)) {
  & $loadScript -ImagesDir $ImagesDir
}

$upArgs = @("up", "-d", "--remove-orphans")
if ($Build) { $upArgs = @("up", "-d", "--build", "--remove-orphans") }
if ($enableAi) { $upArgs = @("--profile", "ai") + $upArgs }

$result = Invoke-DocuForgeCompose -ComposeArgs $upArgs -Capture
if ($result.ExitCode -ne 0) {
  Write-Output "ERROR|COMPOSE_UP|$($result.ExitCode)"
  exit $result.ExitCode
}

if ($WaitHealthy) {
  $port = if ($map["BACKEND_PORT"]) { $map["BACKEND_PORT"] } else { "18081" }
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $ok = $false
  while ((Get-Date) -lt $deadline) {
    try {
      $r = Invoke-WebRequest -Uri "http://127.0.0.1:$port/actuator/health/readiness" -UseBasicParsing -TimeoutSec 3
      if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch {
      Start-Sleep -Seconds 3
    }
  }
  if (-not $ok) {
    Write-DocuForgeLog "Backend not healthy after ${TimeoutSeconds}s" "ERROR"
    Write-Output "ERROR|HEALTH_TIMEOUT"
    exit 3
  }
}

Write-DocuForgeLog "Stack started"
Write-Output "OK|STARTED|$(Get-DocuForgeAppUrl)"
exit 0
