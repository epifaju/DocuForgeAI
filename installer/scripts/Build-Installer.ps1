# Build DocuForge Windows installer artifacts
# Prerequisites: .NET 8 SDK (for WPF tray), Inno Setup 6 (ISCC.exe), Docker (for offline image pack)

param(
  [switch]$SkipTray,
  [switch]$SkipImages,
  [switch]$SkipInno,
  [string]$InnoCompiler = ""
)

$ErrorActionPreference = "Stop"
$InstallerRoot = Split-Path -Parent $PSScriptRoot
$RepoRoot = Split-Path -Parent $InstallerRoot
Set-Location $InstallerRoot

Write-Host "== Prepare payload =="
& "$PSScriptRoot\Prepare-Payload.ps1" -RepoRoot $RepoRoot

$publishTray = -not $SkipTray
if ($publishTray) {
  Write-Host "== Publish tray (WPF) =="
  $dotnetCmd = Get-Command dotnet -ErrorAction SilentlyContinue
  $sdkList = $null
  if ($dotnetCmd) {
    $sdkList = & dotnet --list-sdks 2>&1
  }
  if (-not $dotnetCmd -or -not $sdkList) {
    Write-Warning ".NET SDK not found - packaging PowerShell tray fallback only."
    $publishTray = $false
  }
}

$trayOut = Join-Path $InstallerRoot "dist\tray"
New-Item -ItemType Directory -Force -Path $trayOut | Out-Null
Copy-Item (Join-Path $InstallerRoot "Launch-Tray.cmd") (Join-Path $trayOut "Launch-Tray.cmd") -Force
Copy-Item (Join-Path $InstallerRoot "Open-DocuForge.cmd") (Join-Path $trayOut "Open-DocuForge.cmd") -Force
Copy-Item (Join-Path $InstallerRoot "assets\docuforge.ico") (Join-Path $trayOut "docuforge.ico") -Force
Copy-Item (Join-Path $InstallerRoot "Launch-Tray.cmd") (Join-Path $trayOut "DocuForge.Tray.cmd") -Force

if ($publishTray) {
  & dotnet publish (Join-Path $InstallerRoot "tray\DocuForge.Tray.csproj") `
    -c Release -p:PublishSingleFile=true -o $trayOut
  if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed" }
} else {
  Set-Content -Path (Join-Path $trayOut "README-TRAY.txt") -Encoding UTF8 -Value @(
    "Use Launch-Tray.cmd (PowerShell tray) or install .NET 8 SDK and rebuild for DocuForge.Tray.exe"
  )
}

if (-not $SkipImages) {
  Write-Host "== Optional offline images (docker save) =="
  try {
    & "$PSScriptRoot\Export-OfflineImages.ps1"
  } catch {
    Write-Warning "Offline image export skipped: $($_.Exception.Message)"
  }
}

if (-not $SkipInno) {
  Write-Host "== Compile Inno Setup =="
  if (-not $InnoCompiler) {
    $pf86 = [Environment]::GetFolderPath("ProgramFilesX86")
    $pf = [Environment]::GetFolderPath("ProgramFiles")
    $candidates = @(
      (Join-Path $pf86 "Inno Setup 6\ISCC.exe"),
      (Join-Path $pf "Inno Setup 6\ISCC.exe"),
      (Join-Path $env:LocalAppData "Programs\Inno Setup 6\ISCC.exe")
    )
    foreach ($c in $candidates) {
      if ($c -and (Test-Path -LiteralPath $c)) { $InnoCompiler = $c; break }
    }
  }
  if (-not $InnoCompiler -or -not (Test-Path -LiteralPath $InnoCompiler)) {
    Write-Warning "ISCC.exe not found - skipping installer compile. Install Inno Setup 6, then re-run."
  } else {
    Write-Host "Using ISCC: $InnoCompiler"
    if (-not (Test-Path (Join-Path $trayOut "DocuForge.Tray.exe"))) {
      Write-Host "No Tray EXE: Launch-Tray.cmd will use the PowerShell tray."
    }
    & $InnoCompiler (Join-Path $InstallerRoot "DocuForge.iss")
    if ($LASTEXITCODE -ne 0) { throw "Inno Setup compile failed" }
  }
}

Write-Host "Done. Output under installer\dist\"
