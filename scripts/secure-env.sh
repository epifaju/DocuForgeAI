#!/usr/bin/env sh
# DocuForge AI — generate strong local secrets into .env
# Usage: ./scripts/secure-env.sh [--rotate-postgres] [--show] [--prod]

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

ROTATE_PG=0
SHOW=0
PROD=0
for arg in "$@"; do
  case "$arg" in
    --rotate-postgres) ROTATE_PG=1 ;;
    --show) SHOW=1 ;;
    --prod) PROD=1 ;;
  esac
done

gen_secret() {
  openssl rand -base64 48 | tr -d '=\n' | tr '+/' 'xy'
}

gen_password() {
  openssl rand -base64 32 | tr -d '=\n/+_' | head -c 28
}

set_env() {
  key=$1
  value=$2
  file=.env
  if grep -q "^${key}=" "$file" 2>/dev/null; then
    tmp=$(mktemp)
    awk -v k="$key" -v v="$value" 'BEGIN{FS=OFS="="} $1==k{$0=k"="v} {print}' "$file" > "$tmp"
    mv "$tmp" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

if [ ! -f .env.example ]; then
  echo "ERROR: .env.example missing" >&2
  exit 1
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example"
fi

JWT=$(gen_secret)
ADMIN=$(gen_password)
set_env JWT_SECRET "$JWT"
set_env DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD "$ADMIN"

PG=""
if [ "$ROTATE_PG" -eq 1 ] || [ "$PROD" -eq 1 ]; then
  PG=$(gen_password)
  set_env POSTGRES_PASSWORD "$PG"
fi

if [ "$PROD" -eq 1 ]; then
  set_env APP_ENV production
  set_env DOCUFORGE_BOOTSTRAP_ENABLED false
  set_env ANTIVIRUS_ENABLED true
  echo "Prod flags: APP_ENV=production, bootstrap off, antivirus on"
fi

echo ""
echo "Secrets written to .env (gitignored)."
echo "----------------------------------------------"
if [ "$SHOW" -eq 1 ]; then
  echo "JWT_SECRET=$JWT"
  echo "DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD=$ADMIN"
  [ -n "$PG" ] && echo "POSTGRES_PASSWORD=$PG"
else
  echo "JWT_SECRET=(hidden — pass --show to print once)"
  echo "DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD=(hidden — pass --show)"
  [ -n "$PG" ] && echo "POSTGRES_PASSWORD=(hidden — pass --show)"
fi

echo ""
if [ "$PROD" -eq 1 ]; then
  echo "Next:"
  echo "  1. Set DOCUFORGE_DOMAIN + APP_BASE_URL=https://... and TLS_MODE in .env"
  echo "  2. ./scripts/verify-prod.sh"
  echo "  3. docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile antivirus --profile proxy up -d"
else
  echo "Next: docker compose up -d --force-recreate backend"
  echo "Bootstrap password applies only when no users exist yet."
fi
if [ -n "$PG" ]; then
  echo "Postgres rotated: update role password or recreate volume (down -v)."
fi
echo "Never commit .env."
