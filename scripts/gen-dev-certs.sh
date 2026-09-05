#!/usr/bin/env sh
# Generate self-signed TLS certs for LAN / local prod-like Traefik (TLS_MODE=file)
# Usage: ./scripts/gen-dev-certs.sh [domain]

set -eu
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
DOMAIN=${1:-localhost}
mkdir -p certs
openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -keyout certs/privkey.pem \
  -out certs/fullchain.pem \
  -subj "/CN=${DOMAIN}" \
  -addext "subjectAltName=DNS:${DOMAIN},DNS:localhost,IP:127.0.0.1"
cp infrastructure/traefik/dynamic/tls-file.yml.example infrastructure/traefik/dynamic/tls.yml
echo "Wrote certs/fullchain.pem, certs/privkey.pem and infrastructure/traefik/dynamic/tls.yml"
echo "Set TLS_MODE=file and TRAEFIK_CERT_RESOLVER= (empty) in .env"
