#!/usr/bin/env sh
# DocuForge AI — restore (Phase 19 / PRD §§93–94)
# Usage: ./scripts/restore.sh <backup-dir>
# WARNING: overwrites PostgreSQL data and host storage directory.

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a
  . ./.env
  set +a
fi

BACKUP_DIR="${1:-}"
if [ -z "$BACKUP_DIR" ] || [ ! -d "$BACKUP_DIR" ]; then
  echo "Usage: $0 <backup-dir>"
  echo "Example: $0 ./backups/docuforge-backup-20260904-153000"
  exit 1
fi

# Resolve absolute path
BACKUP_DIR=$(CDPATH= cd -- "$BACKUP_DIR" && pwd)

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-docuforge-postgres}"
POSTGRES_DB="${POSTGRES_DB:-docuforge}"
POSTGRES_USER="${POSTGRES_USER:-docuforge}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-changeme_postgres_dev_only}"
STORAGE_HOST_DIR="${STORAGE_HOST_DIR:-./storage}"

DUMP="$BACKUP_DIR/db/docuforge.sql.gz"
STORAGE_ARCHIVE="$BACKUP_DIR/storage.tar.gz"

if [ ! -f "$DUMP" ]; then
  echo "ERROR: missing $DUMP"
  exit 1
fi
if [ ! -f "$STORAGE_ARCHIVE" ]; then
  echo "ERROR: missing $STORAGE_ARCHIVE"
  exit 1
fi

echo "DocuForge AI restore"
echo "--------------------"
echo "From: $BACKUP_DIR"
echo "WARNING: This will REPLACE database '$POSTGRES_DB' and storage at '$STORAGE_HOST_DIR'."
printf "Type YES to continue: "
read -r confirm
if [ "$confirm" != "YES" ]; then
  echo "Aborted."
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
  echo "ERROR: container '$POSTGRES_CONTAINER' is not running."
  exit 1
fi

echo "Stopping app containers (best effort)…"
docker stop docuforge-backend docuforge-frontend 2>/dev/null || true

echo "Restoring PostgreSQL…"
# Terminate sessions then reload dump (--clean --if-exists in dump handles DROP)
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$POSTGRES_DB' AND pid <> pg_backend_pid();" \
  >/dev/null 2>&1 || true

gunzip -c "$DUMP" | docker exec -i -e PGPASSWORD="$POSTGRES_PASSWORD" "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 >/dev/null

echo "Restoring storage…"
STORAGE_PARENT=$(CDPATH= cd -- "$(dirname -- "$STORAGE_HOST_DIR")" && pwd)
STORAGE_NAME=$(basename -- "$STORAGE_HOST_DIR")
# Backup current storage aside
if [ -d "$STORAGE_HOST_DIR" ]; then
  STAMP=$(date +%Y%m%d-%H%M%S)
  mv "$STORAGE_HOST_DIR" "${STORAGE_HOST_DIR}.pre-restore-${STAMP}"
  echo "Previous storage moved to ${STORAGE_HOST_DIR}.pre-restore-${STAMP}"
fi
tar -C "$STORAGE_PARENT" -xzf "$STORAGE_ARCHIVE"
# Ensure expected name if archive used different basename
if [ ! -d "$STORAGE_HOST_DIR" ] && [ -d "$STORAGE_PARENT/storage" ]; then
  mv "$STORAGE_PARENT/storage" "$STORAGE_HOST_DIR"
fi

echo "--------------------"
echo "Restore complete."
echo "Next:"
echo "  1. docker compose up -d"
echo "  2. ./scripts/healthcheck.sh"
echo "  3. Login and open /documents to verify generated files"
if [ -f "$BACKUP_DIR/config/env.secrets" ] && [ ! -f .env ]; then
  echo "  Tip: restore secrets with: cp \"$BACKUP_DIR/config/env.secrets\" .env"
fi
