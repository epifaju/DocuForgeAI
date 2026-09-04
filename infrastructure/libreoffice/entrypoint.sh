#!/bin/sh
set -eu

UNO_HOST="${LIBREOFFICE_UNO_HOST:-0.0.0.0}"
UNO_PORT="${LIBREOFFICE_UNO_PORT:-2002}"
HEALTH_PORT="${LIBREOFFICE_HEALTH_PORT:-8081}"
PROFILE_DIR="${HOME}/.config/libreoffice-docuforge"

mkdir -p "${PROFILE_DIR}" /tmp/docuforge-lo

echo "Starting LibreOffice UNO listener on ${UNO_HOST}:${UNO_PORT}"
soffice \
  --headless \
  --nologo \
  --nofirststartwizard \
  --norestore \
  --nodefault \
  --nolockcheck \
  -env:UserInstallation="file://${PROFILE_DIR}" \
  --accept="socket,host=${UNO_HOST},port=${UNO_PORT};urp;StarOffice.ServiceManager" \
  &
SOFFICE_PID=$!

cleanup() {
  echo "Stopping LibreOffice (pid ${SOFFICE_PID})"
  kill "${SOFFICE_PID}" 2>/dev/null || true
  wait "${SOFFICE_PID}" 2>/dev/null || true
}
trap cleanup INT TERM EXIT

echo "Starting health server on :${HEALTH_PORT}"
exec python3 /usr/local/bin/health_server.py \
  --port "${HEALTH_PORT}" \
  --uno-host 127.0.0.1 \
  --uno-port "${UNO_PORT}" \
  --soffice-pid "${SOFFICE_PID}"