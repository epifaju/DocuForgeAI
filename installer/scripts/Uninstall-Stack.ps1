# DocuForge AI — uninstall compose project only (never touch unrelated containers)
param(
  [switch]$RemoveVolumes,
  [switch]$RemoveImages,
  [switch]$Force
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

Write-DocuForgeLog "Uninstall start removeVolumes=$RemoveVolumes removeImages=$RemoveImages"

if (Test-DocuForgeDockerReady) {
  $downArgs = @("down", "--remove-orphans")
  if ($RemoveVolumes) { $downArgs += "-v" }
  $result = Invoke-DocuForgeCompose -ComposeArgs $downArgs -Capture
  Write-DocuForgeLog "compose down exit=$($result.ExitCode)"

  if ($RemoveImages) {
    $images = & docker images --format "{{.Repository}}:{{.Tag}}" 2>$null |
      Where-Object { $_ -like "$($script:DocuForgeImagePrefix)*" -or $_ -like "docuforge-ai-*" }
    foreach ($img in $images) {
      Write-DocuForgeLog "Removing image $img"
      & docker rmi -f $img 2>&1 | ForEach-Object { Write-DocuForgeLog ($_ | Out-String).TrimEnd() }
    }
  }
} else {
  Write-DocuForgeLog "Docker not ready — skipped compose down" "WARN"
}

# Do not remove Rancher Desktop / WSL
Write-Output "OK|UNINSTALLED|volumesRemoved=$RemoveVolumes"
exit 0
