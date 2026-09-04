# DocuForge AI — restore (Phase 19 / PRD §§93–94)
# Usage: powershell -File .\scripts\restore.ps1 -BackupDir .\backups\docuforge-backup-...
# WARNING: overwrites PostgreSQL data and host storage directory.

param(
  [Parameter(Mandatory = $true)]
  [string]$BackupDir,
  [switch]$Force
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (Test-Path ".env") {
  Get-Content ".env" | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
    $parts = $_.Split("=", 2)
    if ($parts.Length -eq 2) {
      Set-Item -Path "Env:$($parts[0].Trim())" -Value $parts[1].Trim()
    }
  }
}

$BackupDir = (Resolve-Path $BackupDir).Path
$PostgresContainer = if ($env:POSTGRES_CONTAINER) { $env:POSTGRES_CONTAINER } else { "docuforge-postgres" }
$PostgresDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "docuforge" }
$PostgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "docuforge" }
$PostgresPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "changeme_postgres_dev_only" }
$StorageHostDir = if ($env:STORAGE_HOST_DIR) { $env:STORAGE_HOST_DIR } else { ".\storage" }

$dump = Join-Path $BackupDir "db\docuforge.sql.gz"
$storageArchive = Join-Path $BackupDir "storage.tar.gz"

if (-not (Test-Path $dump)) { Write-Error "Missing $dump" }
if (-not (Test-Path $storageArchive)) { Write-Error "Missing $storageArchive" }

Write-Host "DocuForge AI restore"
Write-Host "--------------------"
Write-Host "From: $BackupDir"
Write-Host "WARNING: This will REPLACE database '$PostgresDb' and storage at '$StorageHostDir'."

if (-not $Force) {
  $confirm = Read-Host "Type YES to continue"
  if ($confirm -ne "YES") {
    Write-Host "Aborted."
    exit 1
  }
}

$running = docker ps --format "{{.Names}}"
if ($running -notcontains $PostgresContainer) {
  Write-Error "Container '$PostgresContainer' is not running."
}

Write-Host "Stopping app containers (best effort)…"
docker stop docuforge-backend docuforge-frontend 2>$null | Out-Null

Write-Host "Restoring PostgreSQL…"
docker exec -e "PGPASSWORD=$PostgresPassword" $PostgresContainer `
  psql -U $PostgresUser -d $PostgresDb -v ON_ERROR_STOP=1 `
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$PostgresDb' AND pid <> pg_backend_pid();" `
  2>$null | Out-Null

$tmpSql = Join-Path $env:TEMP ("docuforge-restore-{0}.sql" -f ([guid]::NewGuid().ToString("N")))
$in = [System.IO.File]::OpenRead($dump)
$gzip = New-Object System.IO.Compression.GZipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
$out = [System.IO.File]::Create($tmpSql)
$gzip.CopyTo($out)
$out.Close(); $gzip.Close(); $in.Close()

Get-Content -Raw $tmpSql | docker exec -i -e "PGPASSWORD=$PostgresPassword" $PostgresContainer `
  psql -U $PostgresUser -d $PostgresDb -v ON_ERROR_STOP=1 | Out-Null
Remove-Item $tmpSql -Force -ErrorAction SilentlyContinue

Write-Host "Restoring storage…"
$storageAbs = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($StorageHostDir)
$parent = Split-Path -Parent $storageAbs
if (Test-Path $storageAbs) {
  $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $aside = "$storageAbs.pre-restore-$stamp"
  Move-Item $storageAbs $aside
  Write-Host "Previous storage moved to $aside"
}
& tar -C $parent -xzf $storageArchive
if ($LASTEXITCODE -ne 0) { Write-Error "tar extract failed" }

Write-Host "--------------------"
Write-Host "Restore complete."
Write-Host "Next:"
Write-Host "  1. docker compose up -d"
Write-Host "  2. powershell -File .\scripts\healthcheck.ps1"
Write-Host "  3. Login and open /documents to verify generated files"
