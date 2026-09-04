#!/usr/bin/env sh
# DocuForge AI — infrastructure health probe (Phase 1)
# Usage: ./scripts/healthcheck.sh
# Windows: use scripts/healthcheck.ps1 or Git Bash.

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a
  . ./.env
  set +a
fi

POSTGRES_PORT="${POSTGRES_PORT:-5434}"
MAILPIT_UI_PORT="${MAILPIT_UI_PORT:-8028}"
OLLAMA_PORT="${OLLAMA_PORT:-11435}"
LIBREOFFICE_HEALTH_HOST_PORT="${LIBREOFFICE_HEALTH_HOST_PORT:-8081}"

fail=0

check_http() {
  name=$1
  url=$2
  if curl -fsS "$url" >/dev/null 2>&1; then
    echo "OK   $name ($url)"
  else
    echo "FAIL $name ($url)"
    fail=1
  fi
}

check_tcp() {
  name=$1
  host=$2
  port=$3
  if command -v nc >/dev/null 2>&1 && nc -z "$host" "$port" >/dev/null 2>&1; then
    echo "OK   $name ($host:$port)"
  elif (echo >/dev/tcp/"$host"/"$port") >/dev/null 2>&1; then
    echo "OK   $name ($host:$port)"
  else
    echo "FAIL $name ($host:$port)"
    fail=1
  fi
}

echo "DocuForge AI infrastructure healthcheck"
echo "--------------------------------------"

check_tcp "postgres" "127.0.0.1" "$POSTGRES_PORT"
check_http "mailpit" "http://127.0.0.1:${MAILPIT_UI_PORT}/api/v1/info"
check_http "ollama" "http://127.0.0.1:${OLLAMA_PORT}/api/tags"
check_http "libreoffice" "http://127.0.0.1:${LIBREOFFICE_HEALTH_HOST_PORT}/health"

if [ "$fail" -ne 0 ]; then
  echo "--------------------------------------"
  echo "One or more checks failed."
  exit 1
fi

echo "--------------------------------------"
echo "All infrastructure checks passed."