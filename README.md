# DocuForge AI

Générateur intelligent de documents professionnels (DOCX / PDF) pour TPE/PME.

**Statut :** Phase 21 — Packaging (MVP)  
**Mode :** Self-hosted / Docker  
**Langue MVP :** Français  
**Spécification :** [`PRD.md`](./PRD.md)

## Stack

| Couche | Technologie |
|--------|-------------|
| Backend | Java 21+, Spring Boot, Maven |
| Frontend | React, TypeScript, Vite, Tailwind |
| Base | PostgreSQL |
| PDF | LibreOffice + JODConverter |
| IA (optionnelle) | Ollama |
| Email (dev) | Mailpit |

## Démarrage rapide

```bash
cp .env.example .env
./scripts/install.sh
```

Windows :

```powershell
Copy-Item .env.example .env
powershell -File .\scripts\install.ps1
```

Puis ouvrir http://localhost:5174 — société `demo` / `admin@demo.local` / mot de passe bootstrap (voir `.env`).

Guide détaillé : [`docs/installation.md`](./docs/installation.md).

## Ports hôte (défauts)

| Service | Port | Usage |
|---------|------|--------|
| Frontend | 5174 | UI |
| Backend | 18081 | API |
| PostgreSQL | 5434 | Base |
| Mailpit UI | 8028 | Emails DEV |
| Ollama | 11435 | IA locale |

## Configuration

1. Copier `.env.example` → `.env`  
2. Générer des secrets forts : `powershell -File .\scripts\secure-env.ps1 -Show` (ou `./scripts/secure-env.sh --show`)  
3. Ne jamais committer `.env` (couvert par `.gitignore`)  
4. Hors machine locale : `powershell -File .\scripts\secure-env.ps1 -Prod -Show` puis désactiver le bootstrap après le premier admin ; `.\scripts\verify-prod.ps1`

## Documentation

| Doc | Contenu |
|-----|---------|
| [`docs/installation.md`](./docs/installation.md) | Install Docker |
| [`docs/admin.md`](./docs/admin.md) | Exploitation / rôles / users |
| [`docs/template-guide.md`](./docs/template-guide.md) | Modèles DOCX |
| [`docs/api.md`](./docs/api.md) | Endpoints `/api/v1` |
| [`docs/architecture.md`](./docs/architecture.md) | Vue d’ensemble |
| [`docs/security.md`](./docs/security.md) | Durcissement / U0 TLS & secrets |
| [`docs/backup.md`](./docs/backup.md) | Backup / restore |
| [`docs/e2e.md`](./docs/e2e.md) | Playwright |
| [`docs/troubleshooting.md`](./docs/troubleshooting.md) | Dépannage |

Templates démo : [`templates/demo/`](./templates/demo/).

CI : [`.github/workflows/ci.yml`](./.github/workflows/ci.yml) (`mvn verify`, frontend lint/build, `docker compose config`).

## Scripts

| Script | Rôle |
|--------|------|
| `scripts/install.*` | Build + up + health |
| `scripts/healthcheck.*` | Sonde infra |
| `scripts/secure-env.*` | Secrets (option `-Prod` / `--prod`) |
| `scripts/verify-prod.*` | Checklist déploiement U0 |
| `scripts/gen-dev-certs.*` | Certificats auto-signés (TLS file) |
| `scripts/backup.*` / `restore.*` / `verify-backup.*` | Sauvegarde |

## Tests

```bash
# Backend
cd backend && mvn -B verify

# E2E (stack déjà démarrée)
cd tests/e2e && npm install && npx playwright install chromium && npm test
```

## Licence

Voir [`LICENSE`](./LICENSE).
