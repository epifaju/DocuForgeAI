#!/usr/bin/env sh
# DocuForge AI — verify .env readiness for production-like deploy (U0)
# Usage: ./scripts/verify-prod.sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if [ ! -f .env ]; then
  echo "FAIL: .env missing — copy .env.example and run scripts/secure-env.sh --prod" >&2
  exit 1
fi

# shellcheck disable=SC1091
set -a
# shellcheck source=/dev/null
. ./.env
set +a

fail=0
warn() { echo "WARN: $*"; }
die() { echo "FAIL: $*"; fail=1; }
ok() { echo "OK: $*"; }

case "${APP_ENV:-}" in
  production|prod) ok "APP_ENV=$APP_ENV" ;;
  *) die "APP_ENV must be production (got '${APP_ENV:-}')" ;;
esac

jwt="${JWT_SECRET:-}"
if [ ${#jwt} -lt 32 ]; then
  die "JWT_SECRET too short (<32)"
elif echo "$jwt" | grep -qi '^changeme'; then
  die "JWT_SECRET is still a placeholder"
else
  ok "JWT_SECRET length=${#jwt}"
fi

pg="${POSTGRES_PASSWORD:-}"
if echo "$pg" | grep -qi '^changeme'; then
  die "POSTGRES_PASSWORD is still a placeholder"
elif [ -z "$pg" ]; then
  die "POSTGRES_PASSWORD missing"
else
  ok "POSTGRES_PASSWORD set"
fi

case "${DOCUFORGE_BOOTSTRAP_ENABLED:-true}" in
  false|FALSE|0|no|NO) ok "bootstrap disabled" ;;
  *) warn "DOCUFORGE_BOOTSTRAP_ENABLED=${DOCUFORGE_BOOTSTRAP_ENABLED:-} — must be false after first admin" ;;
esac

case "${ANTIVIRUS_ENABLED:-false}" in
  true|TRUE|1|yes|YES) ok "antivirus enabled" ;;
  *) warn "ANTIVIRUS_ENABLED is not true — enable for production uploads" ;;
esac

domain="${DOCUFORGE_DOMAIN:-}"
if [ -z "$domain" ]; then
  warn "DOCUFORGE_DOMAIN unset (needed for Traefik Host rule)"
else
  ok "DOCUFORGE_DOMAIN=$domain"
fi

base="${APP_BASE_URL:-}"
case "$base" in
  https://*) ok "APP_BASE_URL=$base" ;;
  *) warn "APP_BASE_URL should be https://... (got '${base:-}')" ;;
esac

tls_mode="${TLS_MODE:-acme}"
case "$tls_mode" in
  acme)
    ok "TLS_MODE=acme"
    if [ -z "${ACME_EMAIL:-}" ] || [ "${ACME_EMAIL}" = "admin@example.com" ]; then
      warn "Set ACME_EMAIL to a real address for Let's Encrypt"
    else
      ok "ACME_EMAIL set"
    fi
    ;;
  file)
    ok "TLS_MODE=file"
    if [ ! -f certs/fullchain.pem ] || [ ! -f certs/privkey.pem ]; then
      die "TLS_MODE=file requires certs/fullchain.pem and certs/privkey.pem"
    else
      ok "certs present"
    fi
    if [ ! -f infrastructure/traefik/dynamic/tls.yml ]; then
      die "Copy infrastructure/traefik/dynamic/tls-file.yml.example to tls.yml"
    else
      ok "traefik dynamic tls.yml present"
    fi
    if [ -n "${TRAEFIK_CERT_RESOLVER:-x}" ] && [ "${TRAEFIK_CERT_RESOLVER}" != "" ]; then
      warn "For file TLS set TRAEFIK_CERT_RESOLVER= (empty) and use docker-compose.tls-file.yml"
    fi
    ;;
  *)
    die "TLS_MODE must be acme or file (got '$tls_mode')"
    ;;
esac

echo ""
if [ "$fail" -ne 0 ]; then
  echo "verify-prod: FAILED"
  exit 1
fi
echo "verify-prod: PASSED"
echo "Suggested up:"
echo "  docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.antivirus.yml \\"
echo "    --profile antivirus --profile proxy up -d"
if [ "$tls_mode" = "file" ]; then
  echo "  (add -f docker-compose.tls-file.yml for file certificates)"
fi
