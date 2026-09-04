# Validate a DocuForge backup directory (structure + checksums).
# Usage: powershell -File .\scripts\verify-backup.ps1 -BackupDir .\backups\docuforge-backup-...

param(
  [Parameter(Mandatory = $true)]
  [string]$BackupDir
)

$ErrorActionPreference = "Stop"
$BackupDir = (Resolve-Path $BackupDir).Path
$fail = $false

function Need([string]$Path) {
  if (Test-Path $Path) {
    Write-Host ("OK   {0}" -f $Path)
  } else {
    Write-Host ("FAIL missing {0}" -f $Path)
    $script:fail = $true
  }
}

Write-Host "Verifying backup: $BackupDir"
Need (Join-Path $BackupDir "MANIFEST.txt")
Need (Join-Path $BackupDir "db\docuforge.sql.gz")
Need (Join-Path $BackupDir "storage.tar.gz")
Need (Join-Path $BackupDir "config")

$checksums = Join-Path $BackupDir "CHECKSUMS.sha256"
if (Test-Path $checksums) {
  Write-Host "Checking SHA-256..."
  Get-Content $checksums | ForEach-Object {
    if ($_ -match '^\s*$') { return }
    $parts = $_ -split '\s+', 2
    if ($parts.Length -lt 2) { return }
    $expected = $parts[0].ToLowerInvariant()
    $rel = ($parts[1].Trim() -replace "/", "\")
    $resolved = Join-Path $BackupDir $rel
    if (-not (Test-Path $resolved)) {
      Write-Host ("FAIL checksum file missing: {0}" -f $parts[1])
      $script:fail = $true
      return
    }
    $actual = (Get-FileHash -Algorithm SHA256 $resolved).Hash.ToLowerInvariant()
    if ($actual -eq $expected) {
      Write-Host ("OK   checksum {0}" -f $parts[1].Trim())
    } else {
      Write-Host ("FAIL checksum {0}" -f $parts[1].Trim())
      $script:fail = $true
    }
  }
}

$dump = Join-Path $BackupDir "db\docuforge.sql.gz"
try {
  $fs = [System.IO.File]::OpenRead($dump)
  $gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Decompress)
  $buf = New-Object byte[] 64
  [void]$gz.Read($buf, 0, 64)
  $gz.Close(); $fs.Close()
  Write-Host "OK   db dump gzip"
} catch {
  Write-Host "FAIL db dump is not valid gzip"
  $fail = $true
}

$archive = Join-Path $BackupDir "storage.tar.gz"
& tar -tzf $archive > $null 2>&1
if ($LASTEXITCODE -ne 0) {
  Write-Host "FAIL storage archive is not a valid tar.gz"
  $fail = $true
} else {
  Write-Host "OK   storage tar.gz"
}

if ($fail) {
  Write-Host "Backup verification FAILED."
  exit 1
}
Write-Host "Backup verification PASSED."
