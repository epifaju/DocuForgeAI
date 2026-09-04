#!/usr/bin/env sh
# DocuForge AI — generate strong local secrets into .env
# Usage: ./scripts/secure-env.sh [--rotate-postgres] [--show]

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

ROTATE_PG=0
SHOW=0
for arg in "$@"; do
  case "$arg" in
    --rotate-postgres) ROTATE_PG=1 ;;
    --show) SHOW=1 ;;
  esac
done

gen_secret() {
  # 48 bytes → base64url-ish
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
    # portable-ish replace
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
if [ "$ROTATE_PG" -eq 1 ]; then
  PG=$(gen_password)
  set_env POSTGRES_PASSWORD "$PG"
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
echo "Next: docker compose up -d --force-recreate backend"
echo "Bootstrap password applies only when no users exist yet."
if [ "$ROTATE_PG" -eq 1 ]; then
  echo "Postgres rotated: update role password or recreate volume (down -v)."
fi
echo "Never commit .env."
