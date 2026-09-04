#!/usr/bin/env sh
# DocuForge AI — install / first start (Phase 21)
# Usage: ./scripts/install.sh

set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

echo "DocuForge AI — installation"
echo "---------------------------"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example — change changeme_* secrets before production."
else
  echo ".env already present — left unchanged."
fi

echo "Building images…"
docker compose build

echo "Starting stack…"
docker compose up -d

echo "Waiting for backend health…"
i=0
until curl -fsS "http://127.0.0.1:${BACKEND_PORT:-18081}/actuator/health" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "ERROR: backend not healthy after ~2 minutes."
    docker compose ps
    exit 1
  fi
  sleep 2
done

if [ -x ./scripts/healthcheck.sh ]; then
  ./scripts/healthcheck.sh || true
fi

echo "---------------------------"
echo "Installation ready."
echo "  UI:        http://localhost:${FRONTEND_PORT:-5174}"
echo "  API:       http://localhost:${BACKEND_PORT:-18081}"
echo "  Mailpit:   http://localhost:${MAILPIT_UI_PORT:-8028}"
echo "  Login:     company=demo / admin@demo.local / (see .env DOCUFORGE_BOOTSTRAP_*)"
echo "  Docs:      docs/installation.md"
echo "  Templates: templates/demo/"
