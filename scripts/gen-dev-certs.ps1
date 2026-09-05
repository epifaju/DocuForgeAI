# Generate self-signed TLS certs for LAN / local prod-like Traefik (TLS_MODE=file)
# Usage: powershell -File .\scripts\gen-dev-certs.ps1 [-Domain localhost]

param(
  [string]$Domain = "localhost"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

New-Item -ItemType Directory -Force -Path "certs" | Out-Null
& openssl req -x509 -nodes -newkey rsa:2048 -days 825 `
  -keyout "certs/privkey.pem" `
  -out "certs/fullchain.pem" `
  -subj "/CN=$Domain" `
  -addext "subjectAltName=DNS:$Domain,DNS:localhost,IP:127.0.0.1"

Copy-Item "infrastructure/traefik/dynamic/tls-file.yml.example" "infrastructure/traefik/dynamic/tls.yml" -Force
Write-Host "Wrote certs/fullchain.pem, certs/privkey.pem and infrastructure/traefik/dynamic/tls.yml"
Write-Host "Set TLS_MODE=file and TRAEFIK_CERT_RESOLVER= (empty) in .env"
