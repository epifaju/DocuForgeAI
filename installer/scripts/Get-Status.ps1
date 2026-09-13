# Machine-readable status for the tray app
param()

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\Common.ps1"

$docker = Test-DocuForgeDockerReady
$url = Get-DocuForgeAppUrl
$map = Get-DocuForgeEnvMap
$backendPort = if ($map["BACKEND_PORT"]) { $map["BACKEND_PORT"] } else { "18081" }

$uiOk = $false
$apiOk = $false
try {
  $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
  $uiOk = ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500)
} catch {}
try {
  $r2 = Invoke-WebRequest -Uri "http://127.0.0.1:$backendPort/actuator/health/readiness" -UseBasicParsing -TimeoutSec 3
  $apiOk = ($r2.StatusCode -eq 200)
} catch {}

$status = "Offline"
if (-not $docker) { $status = "Erreur" }
elseif ($apiOk) { $status = "EnLigne" }
elseif ($uiOk) { $status = "Demarrage" }
else { $status = "HorsLigne" }

Write-Output ("OK|{0}|docker={1}|api={2}|ui={3}|url={4}" -f $status, $docker, $apiOk, $uiOk, $url)
exit 0
