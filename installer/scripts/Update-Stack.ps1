# Check / apply updates (pull or load offline pack, then recreate)
param(
  [string]$UpdateManifestUrl = "",
  [string]$ImagesDir = "",
  [switch]$Apply
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

$versionPath = Get-DocuForgeVersionInfoPath
$local = @{ version = "0.1.0"; channel = "stable" }
if (Test-Path $versionPath) {
  try { $local = Get-Content $versionPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch {}
}

$map = Get-DocuForgeEnvMap
if (-not $UpdateManifestUrl -and $map["DOCUFORGE_UPDATE_URL"]) {
  $UpdateManifestUrl = $map["DOCUFORGE_UPDATE_URL"]
}

$remote = $null
if ($UpdateManifestUrl) {
  try {
    $remote = Invoke-RestMethod -Uri $UpdateManifestUrl -TimeoutSec 30
  } catch {
    Write-DocuForgeLog "Update check failed: $($_.Exception.Message)" "WARN"
    Write-Output "ERROR|UPDATE_CHECK"
    exit 1
  }
}

if (-not $Apply) {
  if ($null -eq $remote) {
    Write-Output "OK|NO_MANIFEST|local=$($local.version)"
    exit 0
  }
  $newer = $remote.version -ne $local.version
  Write-Output ("OK|CHECK|local={0}|remote={1}|update={2}" -f $local.version, $remote.version, $newer)
  exit 0
}

if (-not (Test-DocuForgeDockerReady)) {
  Write-Output "ERROR|DOCKER_NOT_READY"
  exit 2
}

if (-not $ImagesDir) {
  $ImagesDir = Join-Path (Get-DocuForgeDataRoot) "images"
}
if (Test-Path $ImagesDir) {
  & (Join-Path $PSScriptRoot "Load-OfflineImages.ps1") -ImagesDir $ImagesDir
}

# Prefer pull when online
$pull = Invoke-DocuForgeCompose -ComposeArgs @("pull") -Capture
Write-DocuForgeLog "compose pull exit=$($pull.ExitCode)"

& (Join-Path $PSScriptRoot "Start-Stack.ps1") -WaitHealthy
if ($LASTEXITCODE -ne 0) {
  Write-Output "ERROR|UPDATE_APPLY"
  exit $LASTEXITCODE
}

if ($remote -and $remote.version) {
  @{
    version = $remote.version
    channel = $(if ($remote.channel) { $remote.channel } else { "stable" })
    updatedAt = (Get-Date).ToString("o")
  } | ConvertTo-Json | Set-Content -Path $versionPath -Encoding UTF8
}

Write-Output "OK|UPDATED|$($local.version)"
exit 0
