# DocuForge AI — PostgreSQL dump to a chosen path (no password in logs)
param(
  [Parameter(Mandatory)][string]$OutputFile
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\Common.ps1"

if (-not (Test-DocuForgeDockerReady)) {
  Write-Output "ERROR|DOCKER_NOT_READY"
  exit 2
}

$map = Get-DocuForgeEnvMap
$db = if ($map["POSTGRES_DB"]) { $map["POSTGRES_DB"] } else { "docuforge" }
$user = if ($map["POSTGRES_USER"]) { $map["POSTGRES_USER"] } else { "docuforge" }
$container = if ($map["POSTGRES_CONTAINER"]) { $map["POSTGRES_CONTAINER"] } else { "docuforge-postgres" }

$dir = Split-Path -Parent $OutputFile
if ($dir -and -not (Test-Path $dir)) {
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

Write-DocuForgeLog "Backup database to $OutputFile"
$dump = & docker exec $container pg_dump -U $user -d $db --clean --if-exists 2>&1
if ($LASTEXITCODE -ne 0) {
  Write-DocuForgeLog "pg_dump failed: $dump" "ERROR"
  Write-Output "ERROR|PG_DUMP"
  exit 1
}

Set-Content -Path $OutputFile -Value ($dump | Out-String) -Encoding UTF8
Write-DocuForgeLog "Backup completed ($((Get-Item $OutputFile).Length) bytes)"
Write-Output "OK|$OutputFile"
exit 0
