# LibreOffice (infrastructure)

Conteneur de conversion PDF (PRD §10, §13).

## Capacite

| Port | Role |
|------|------|
| `2002` | Listener UNO LibreOffice headless (JODConverter `ExternalOfficeManager`) |
| `8081` | Health/status HTTP (`GET /health`, `GET /ready`) |

Le backend convertit via `PdfConverter` + JODConverter (`LoadDocumentMode.REMOTE`), sans `soffice` dans le code metier.

Variables utiles :

- `PDF_CONVERSION_ENABLED` — desactive la conversion (statut reste `GENERATED`)
- `LIBREOFFICE_UNO_HOST` / `LIBREOFFICE_UNO_PORT` — cible UNO
- `PDF_CONVERSION_TIMEOUT` — timeout tache (secondes)
- `PDF_CONVERSION_MAX_RETRIES` — nouvelles tentatives apres echec

## Build

```bash
docker compose build libreoffice
```

## Healthcheck

Compose et l'image utilisent `curl http://127.0.0.1:8081/health`.

En mode dev (`docker-compose.dev.yml`), le port UNO `2002` est aussi publie sur l'hote pour un backend JVM local.
