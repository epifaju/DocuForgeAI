# DocuForge AI — detect/install WSL2 + Rancher Desktop (silent where possible)
# Exit codes:
#   0  = ready
#   10 = reboot required (WSL)
#   11 = Rancher install started / needs user finish
#   12 = docker not ready yet (retry)
#   1  = hard failure

param(
  [switch]$InstallMissing,
  [string]$RancherInstallerPath = "",
  [int]$DockerWaitSeconds = 180
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

function Test-Wsl2Ready {
  try {
    $status = & wsl --status 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -and $status -match "not installed|pas install") { return $false }
    # Default version 2 preferred
    $list = & wsl -l -v 2>&1 | Out-String
    if ($list -match "VERSION\s+2" -or $list -match "\s2\s*$" -or $status -match "2") { return $true }
    # WSL present — assume OK on Win11 if wsl --status works
    return ($LASTEXITCODE -eq 0 -or $status -match "Default Version:\s*2")
  } catch {
    return $false
  }
}

function Install-Wsl2Feature {
  Write-DocuForgeLog "Installing WSL2 features"
  # Prefer wsl --install (Win11)
  $out = & wsl --install --no-distribution 2>&1 | Out-String
  Write-DocuForgeLog $out
  if ($out -match "redémarr|restart|reboot|Reboot") {
    return "REBOOT"
  }
  # Fallback DISM
  & dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart 2>&1 | Out-Null
  & dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart 2>&1 | Out-Null
  return "REBOOT"
}

function Test-RancherOrDocker {
  return (Test-DocuForgeDockerReady)
}

function Find-RancherInstaller {
  param([string]$Hint)
  if ($Hint -and (Test-Path $Hint)) { return $Hint }
  $candidates = @(
    (Join-Path (Get-DocuForgeInstallRoot) "prereqs\RancherDesktopSetup.exe"),
    (Join-Path $PSScriptRoot "..\payload\prereqs\RancherDesktopSetup.exe"),
    (Join-Path (Get-DocuForgeDataRoot) "prereqs\RancherDesktopSetup.exe")
  )
  foreach ($c in $candidates) {
    if (Test-Path $c) { return $c }
  }
  return $null
}

function Install-RancherDesktop {
  param([string]$SetupExe)
  if (-not $SetupExe) {
    Write-DocuForgeLog "Rancher Desktop installer not bundled" "ERROR"
    return $false
  }
  Write-DocuForgeLog "Launching Rancher Desktop installer (silent if supported)"
  # NSIS-style silent flags commonly supported by Rancher Desktop installer
  $p = Start-Process -FilePath $SetupExe -ArgumentList "/S","/silent","/quiet" -PassThru -Wait -WindowStyle Hidden
  Write-DocuForgeLog "Rancher installer exit=$($p.ExitCode)"
  return ($p.ExitCode -eq 0 -or $p.ExitCode -eq $null)
}

Write-DocuForgeLog "Prereq check start"

if (-not (Test-Wsl2Ready)) {
  if (-not $InstallMissing) {
    Write-Output "ERROR|WSL2_MISSING"
    exit 1
  }
  $wslResult = Install-Wsl2Feature
  if ($wslResult -eq "REBOOT") {
    # Marker for installer resume after reboot
    $marker = Join-Path (Get-DocuForgeDataRoot) "pending-prereqs.json"
    @{ step = "after-wsl-reboot"; ts = (Get-Date).ToString("o") } | ConvertTo-Json |
      Set-Content -Path $marker -Encoding UTF8
    Write-Output "REBOOT_REQUIRED|WSL2"
    exit 10
  }
}

if (Test-RancherOrDocker) {
  Write-DocuForgeLog "Docker engine ready"
  Write-Output "OK|DOCKER_READY"
  exit 0
}

if (-not $InstallMissing) {
  Write-Output "ERROR|DOCKER_MISSING"
  exit 1
}

$setup = Find-RancherInstaller -Hint $RancherInstallerPath
if (-not $setup) {
  Write-DocuForgeLog "No Rancher installer — guide user to download" "WARN"
  Write-Output "NEED_RANCHER|https://rancherdesktop.io/"
  exit 11
}

$installed = Install-RancherDesktop -SetupExe $setup
if (-not $installed) {
  Write-Output "ERROR|RANCHER_INSTALL_FAILED"
  exit 1
}

# Wait for docker CLI
$deadline = (Get-Date).AddSeconds($DockerWaitSeconds)
while ((Get-Date) -lt $deadline) {
  if (Test-DocuForgeDockerReady) {
    Write-Output "OK|DOCKER_READY"
    exit 0
  }
  Start-Sleep -Seconds 5
}

Write-DocuForgeLog "Docker still not ready after wait — user may need to start Rancher once" "WARN"
Write-Output "RETRY|START_RANCHER_DESKTOP"
exit 12
