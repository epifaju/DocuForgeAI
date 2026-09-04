#!/usr/bin/env sh
# Validate a DocuForge backup directory (structure + checksums).
# Usage: ./scripts/verify-backup.sh <backup-dir>

set -eu

BACKUP_DIR="${1:-}"
if [ -z "$BACKUP_DIR" ] || [ ! -d "$BACKUP_DIR" ]; then
  echo "Usage: $0 <backup-dir>"
  exit 1
fi

BACKUP_DIR=$(CDPATH= cd -- "$BACKUP_DIR" && pwd)
fail=0

need() {
  if [ -f "$1" ] || [ -d "$1" ]; then
    echo "OK   $1"
  else
    echo "FAIL missing $1"
    fail=1
  fi
}

echo "Verifying backup: $BACKUP_DIR"
need "$BACKUP_DIR/MANIFEST.txt"
need "$BACKUP_DIR/db/docuforge.sql.gz"
need "$BACKUP_DIR/storage.tar.gz"
need "$BACKUP_DIR/config"

if [ -f "$BACKUP_DIR/CHECKSUMS.sha256" ]; then
  echo "Checking SHA-256…"
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$BACKUP_DIR" && sha256sum -c CHECKSUMS.sha256) || fail=1
  elif command -v shasum >/dev/null 2>&1; then
    (cd "$BACKUP_DIR" && shasum -a 256 -c CHECKSUMS.sha256) || fail=1
  else
    echo "WARN no sha256 tool; skipped checksum verification"
  fi
fi

# Smoke: gzip readable + tar listable
if ! gzip -t "$BACKUP_DIR/db/docuforge.sql.gz" 2>/dev/null; then
  echo "FAIL db dump is not valid gzip"
  fail=1
else
  echo "OK   db dump gzip"
fi

if ! tar -tzf "$BACKUP_DIR/storage.tar.gz" >/dev/null 2>&1; then
  echo "FAIL storage archive is not a valid tar.gz"
  fail=1
else
  echo "OK   storage tar.gz"
fi

if [ "$fail" -ne 0 ]; then
  echo "Backup verification FAILED."
  exit 1
fi

echo "Backup verification PASSED."
