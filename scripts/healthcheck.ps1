# DocuForge AI — infrastructure health probe (Phase 1)
# Usage: powershell -File .\scripts\healthcheck.ps1

$ErrorActionPreference = "Continue"
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

$postgresPort = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { "5434" }
$mailpitPort = if ($env:MAILPIT_UI_PORT) { $env:MAILPIT_UI_PORT } else { "8028" }
$ollamaPort = if ($env:OLLAMA_PORT) { $env:OLLAMA_PORT } else { "11435" }
$loPort = if ($env:LIBREOFFICE_HEALTH_HOST_PORT) { $env:LIBREOFFICE_HEALTH_HOST_PORT } else { "8081" }

$script:fail = $false

function Test-Tcp([string]$Name, [string]$TargetHost, [int]$Port) {
  try {
    $client = New-Object System.Net.Sockets.TcpClient
    $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
    $ok = $iar.AsyncWaitHandle.WaitOne(2000, $false)
    if (-not $ok) { throw "timeout" }
    $client.EndConnect($iar)
    $client.Close()
    Write-Host ("OK   {0} ({1}:{2})" -f $Name, $TargetHost, $Port)
  } catch {
    Write-Host ("FAIL {0} ({1}:{2})" -f $Name, $TargetHost, $Port)
    $script:fail = $true
  }
}

function Test-Http([string]$Name, [string]$Url) {
  try {
    $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300) {
      Write-Host ("OK   {0} ({1})" -f $Name, $Url)
    } else {
      Write-Host ("FAIL {0} ({1}) status={2}" -f $Name, $Url, $resp.StatusCode)
      $script:fail = $true
    }
  } catch {
    Write-Host ("FAIL {0} ({1})" -f $Name, $Url)
    $script:fail = $true
  }
}

Write-Host "DocuForge AI infrastructure healthcheck"
Write-Host "--------------------------------------"
Test-Tcp "postgres" "127.0.0.1" ([int]$postgresPort)
Test-Http "mailpit" ("http://127.0.0.1:{0}/api/v1/info" -f $mailpitPort)
Test-Http "ollama" ("http://127.0.0.1:{0}/api/tags" -f $ollamaPort)
Test-Http "libreoffice" ("http://127.0.0.1:{0}/health" -f $loPort)

if ($script:fail) {
  Write-Host "--------------------------------------"
  Write-Host "One or more checks failed."
  exit 1
}

Write-Host "--------------------------------------"
Write-Host "All infrastructure checks passed."