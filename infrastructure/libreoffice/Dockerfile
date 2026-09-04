# DocuForge AI â€” LibreOffice headless (Phase 1)
# UNO socket :2002 for future JODConverter ExternalOfficeManager / local pool (Phase 10).
# HTTP health/status :8081 matches LIBREOFFICE_URL (conversion REST API arrives in Phase 10).
FROM debian:bookworm-slim

LABEL org.opencontainers.image.title="docuforge-ai-libreoffice" \
      org.opencontainers.image.description="LibreOffice headless with UNO listener and HTTP health endpoint"

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LIBREOFFICE_UNO_HOST=0.0.0.0 \
    LIBREOFFICE_UNO_PORT=2002 \
    LIBREOFFICE_HEALTH_PORT=8081 \
    HOME=/home/docuforge

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        fontconfig \
        fonts-dejavu \
        libreoffice-calc-nogui \
        libreoffice-java-common \
        libreoffice-writer-nogui \
        python3 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --home-dir /home/docuforge docuforge \
    && mkdir -p /home/docuforge/.config /tmp/docuforge-lo \
    && chown -R docuforge:docuforge /home/docuforge /tmp/docuforge-lo

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
COPY health_server.py /usr/local/bin/health_server.py
RUN chmod +x /usr/local/bin/entrypoint.sh /usr/local/bin/health_server.py

USER docuforge
WORKDIR /home/docuforge

EXPOSE 2002 8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${LIBREOFFICE_HEALTH_PORT:-8081}/health" || exit 1

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]