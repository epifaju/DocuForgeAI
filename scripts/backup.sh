#!/usr/bin/env sh
# DocuForge AI — backup (Phase 19 / PRD §§93–94)
# Usage: ./scripts/backup.sh [output-parent-dir]
# Produces: <parent>/docuforge-backup-YYYYMMDD-HHMMSS/
#   MANIFEST.txt
#   db/docuforge.sql.gz
#   storage.tar.gz
#   config/ (env.example, compose files, optional .env copy)

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a
  . ./.env
  set +a
fi

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-docuforge-postgres}"
POSTGRES_DB="${POSTGRES_DB:-docuforge}"
POSTGRES_USER="${POSTGRES_USER:-docuforge}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-changeme_postgres_dev_only}"
STORAGE_HOST_DIR="${STORAGE_HOST_DIR:-./storage}"
BACKUP_PARENT="${1:-${BACKUP_DIR:-./backups}}"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="${BACKUP_PARENT%/}/docuforge-backup-${TIMESTAMP}"

echo "DocuForge AI backup"
echo "-------------------"
echo "Target: $BACKUP_DIR"

mkdir -p "$BACKUP_DIR/db" "$BACKUP_DIR/config"

# --- PostgreSQL ---
if ! docker ps --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
  echo "ERROR: container '$POSTGRES_CONTAINER' is not running."
  echo "Start stack: docker compose up -d postgres"
  exit 1
fi

echo "Dumping PostgreSQL ($POSTGRES_DB)…"
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-acl \
  | gzip -c > "$BACKUP_DIR/db/docuforge.sql.gz"

# --- Storage (templates + generated + temporary) ---
STORAGE_ABS=$(CDPATH= cd -- "$STORAGE_HOST_DIR" && pwd)
echo "Archiving storage ($STORAGE_ABS)…"
if command -v tar >/dev/null 2>&1; then
  tar -C "$(dirname -- "$STORAGE_ABS")" -czf "$BACKUP_DIR/storage.tar.gz" "$(basename -- "$STORAGE_ABS")"
else
  echo "ERROR: tar is required to archive storage."
  exit 1
fi

# --- Configuration ---
cp -f .env.example "$BACKUP_DIR/config/env.example" 2>/dev/null || true
cp -f docker-compose.yml "$BACKUP_DIR/config/docker-compose.yml" 2>/dev/null || true
cp -f docker-compose.dev.yml "$BACKUP_DIR/config/docker-compose.dev.yml" 2>/dev/null || true
if [ -f .env ]; then
  cp -f .env "$BACKUP_DIR/config/env.secrets"
  echo "WARNING: config/env.secrets contains secrets — protect this backup."
fi

{
  echo "DocuForge AI backup manifest"
  echo "created_at=$TIMESTAMP"
  echo "postgres_container=$POSTGRES_CONTAINER"
  echo "postgres_db=$POSTGRES_DB"
  echo "storage_source=$STORAGE_ABS"
  echo "host=$(uname -n 2>/dev/null || echo unknown)"
  echo "docker=$(docker --version 2>/dev/null || echo unknown)"
  echo "contents=db/docuforge.sql.gz,storage.tar.gz,config/"
} > "$BACKUP_DIR/MANIFEST.txt"

# Integrity checksums
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && sha256sum db/docuforge.sql.gz storage.tar.gz > CHECKSUMS.sha256)
elif command -v shasum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && shasum -a 256 db/docuforge.sql.gz storage.tar.gz > CHECKSUMS.sha256)
fi

echo "-------------------"
echo "Backup complete: $BACKUP_DIR"
ls -la "$BACKUP_DIR"
echo ""
echo "Verify structure: ./scripts/verify-backup.sh \"$BACKUP_DIR\""
