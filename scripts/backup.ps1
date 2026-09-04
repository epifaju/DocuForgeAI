# DocuForge AI — backup (Phase 19 / PRD §§93–94)
# Usage: powershell -File .\scripts\backup.ps1 [[-BackupParent] <dir>]

param(
  [string]$BackupParent = ""
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

$PostgresContainer = if ($env:POSTGRES_CONTAINER) { $env:POSTGRES_CONTAINER } else { "docuforge-postgres" }
$PostgresDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "docuforge" }
$PostgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "docuforge" }
$PostgresPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "changeme_postgres_dev_only" }
$StorageHostDir = if ($env:STORAGE_HOST_DIR) { $env:STORAGE_HOST_DIR } else { ".\storage" }

if (-not $BackupParent) {
  $BackupParent = if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { ".\backups" }
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $BackupParent "docuforge-backup-$stamp"

Write-Host "DocuForge AI backup"
Write-Host "-------------------"
Write-Host "Target: $BackupDir"

New-Item -ItemType Directory -Force -Path (Join-Path $BackupDir "db") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $BackupDir "config") | Out-Null

$running = docker ps --format "{{.Names}}"
if ($running -notcontains $PostgresContainer) {
  Write-Error "Container '$PostgresContainer' is not running. Start: docker compose up -d postgres"
}

Write-Host "Dumping PostgreSQL ($PostgresDb)…"
$dumpPath = Join-Path $BackupDir "db\docuforge.sql.gz"
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "docker"
$psi.Arguments = "exec -e PGPASSWORD=$PostgresPassword $PostgresContainer pg_dump -U $PostgresUser -d $PostgresDb --clean --if-exists --no-owner --no-acl"
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$proc = [System.Diagnostics.Process]::Start($psi)
$stdout = $proc.StandardOutput.BaseStream
$fs = [System.IO.File]::Create($dumpPath)
$gzip = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Compress)
$stdout.CopyTo($gzip)
$gzip.Close()
$fs.Close()
$proc.WaitForExit()
if ($proc.ExitCode -ne 0) {
  $err = $proc.StandardError.ReadToEnd()
  Write-Error "pg_dump failed: $err"
}

Write-Host "Archiving storage ($StorageHostDir)…"
$storageArchive = Join-Path $BackupDir "storage.tar.gz"
$storageAbs = (Resolve-Path $StorageHostDir).Path
$parent = Split-Path -Parent $storageAbs
$name = Split-Path -Leaf $storageAbs
# Prefer tar (Windows 10+ / Git Bash); fallback to Compress-Archive as zip renamed note
if (Get-Command tar -ErrorAction SilentlyContinue) {
  & tar -C $parent -czf $storageArchive $name
  if ($LASTEXITCODE -ne 0) { Write-Error "tar failed" }
} else {
  Write-Error "tar is required (Windows 10+ or Git for Windows)."
}

if (Test-Path ".env.example") {
  Copy-Item ".env.example" (Join-Path $BackupDir "config\env.example") -Force
}
if (Test-Path "docker-compose.yml") {
  Copy-Item "docker-compose.yml" (Join-Path $BackupDir "config\docker-compose.yml") -Force
}
if (Test-Path "docker-compose.dev.yml") {
  Copy-Item "docker-compose.dev.yml" (Join-Path $BackupDir "config\docker-compose.dev.yml") -Force
}
if (Test-Path ".env") {
  Copy-Item ".env" (Join-Path $BackupDir "config\env.secrets") -Force
  Write-Host "WARNING: config\env.secrets contains secrets - protect this backup."
}

$dockerVersion = try { (docker --version) -join " " } catch { "unknown" }
$manifestLines = @(
  "DocuForge AI backup manifest",
  "created_at=$stamp",
  "postgres_container=$PostgresContainer",
  "postgres_db=$PostgresDb",
  "storage_source=$storageAbs",
  "host=$env:COMPUTERNAME",
  "docker=$dockerVersion",
  "contents=db/docuforge.sql.gz|storage.tar.gz|config/"
)
Set-Content -Path (Join-Path $BackupDir "MANIFEST.txt") -Value $manifestLines -Encoding UTF8

$checksumLines = @()
foreach ($relPath in @("db/docuforge.sql.gz", "storage.tar.gz")) {
  $full = Join-Path $BackupDir ($relPath -replace "/", "\")
  $hash = (Get-FileHash -Algorithm SHA256 $full).Hash.ToLowerInvariant()
  $checksumLines += "$hash  $relPath"
}
Set-Content -Path (Join-Path $BackupDir "CHECKSUMS.sha256") -Value $checksumLines -Encoding ASCII

Write-Host "-------------------"
Write-Host "Backup complete: $BackupDir"
Get-ChildItem $BackupDir | Format-Table Name, Length
Write-Host ("Verify: powershell -File .\scripts\verify-backup.ps1 -BackupDir {0}" -f $BackupDir)
