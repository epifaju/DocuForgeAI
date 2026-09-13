# DocuForge AI — stop / restart stack
param(
  [ValidateSet("Stop", "Restart")][string]$Action = "Stop",
  [switch]$RemoveOrphans
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

if (-not (Test-DocuForgeDockerReady)) {
  Write-Output "ERROR|DOCKER_NOT_READY"
  exit 2
}

if ($Action -eq "Stop") {
  $args = @("stop")
  $result = Invoke-DocuForgeCompose -ComposeArgs $args -Capture
  if ($result.ExitCode -ne 0) {
    Write-Output "ERROR|STOP|$($result.ExitCode)"
    exit $result.ExitCode
  }
  Write-DocuForgeLog "Stack stopped"
  Write-Output "OK|STOPPED"
  exit 0
}

# Restart
$null = Invoke-DocuForgeCompose -ComposeArgs @("stop") -Capture
$start = Join-Path $PSScriptRoot "Start-Stack.ps1"
& $start -WaitHealthy
exit $LASTEXITCODE
