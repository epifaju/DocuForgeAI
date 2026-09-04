# Installation

Guide d’installation self-hosted — Phase 21 / PRD §101.25.

## Prérequis

- Docker Desktop (Windows/macOS) **ou** Docker Engine + Compose v2+
- Ports libres (défauts) : `5174` (UI), `18081` (API), `5434` (Postgres), `8028`/`1028` (Mailpit), `11435` (Ollama)
- ~4 Go RAM recommandés (LibreOffice + Postgres + backend)

## Installation rapide

**Linux / macOS / Git Bash :**

```bash
cp .env.example .env
# Éditer .env : changer tous les changeme_* avant usage hors machine locale
chmod +x scripts/install.sh scripts/healthcheck.sh
./scripts/install.sh
```

**Windows (PowerShell) :**

```powershell
Copy-Item .env.example .env
# Éditer .env : changer tous les changeme_* 
powershell -File .\scripts\install.ps1
```

L’install construit les images, démarre la stack, attend le health backend, puis affiche les URLs.

Équivalent manuel :

```bash
docker compose build
docker compose up -d
docker compose ps
./scripts/healthcheck.sh   # ou healthcheck.ps1
```

## Première connexion

| Champ | Valeur (bootstrap DEV) |
|-------|-------------------------|
| Société | `demo` |
| Email | `admin@demo.local` |
| Mot de passe | `changeme_admin_dev_only` (variable `DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD`) |

UI : http://localhost:5174  
API : http://localhost:18081  
Mailpit : http://localhost:8028  

Désactiver le bootstrap en production : `DOCUFORGE_BOOTSTRAP_ENABLED=false` après création du premier admin.

## Secrets à changer

Avant tout déploiement non local :

```powershell
powershell -File .\scripts\secure-env.ps1 -Show
# optionnel: -RotatePostgres  (attention: volume Postgres existant)
docker compose up -d --force-recreate backend
```

- `POSTGRES_PASSWORD` / `JWT_SECRET` / `DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD`
- Identifiants SMTP si hors Mailpit

Ne jamais committer `.env` (voir `.gitignore`). Le mot de passe bootstrap ne s’applique **que** s’il n’existe encore aucun utilisateur.

## Ports hôte (défauts)

| Variable | Défaut | Rôle |
|----------|--------|------|
| `FRONTEND_PORT` | `5174` | UI |
| `BACKEND_PORT` | `18081` | API |
| `POSTGRES_PORT` | `5434` | PostgreSQL |
| `MAILPIT_UI_PORT` | `8028` | UI Mailpit |
| `MAILPIT_SMTP_PORT` | `1028` | SMTP Mailpit (hôte) |
| `OLLAMA_PORT` | `11435` | Ollama |
| `LIBREOFFICE_HEALTH_HOST_PORT` | `8081` | Health LO (compose dev) |

## Profils optionnels

```bash
# Antivirus ClamAV
ANTIVIRUS_ENABLED=true docker compose --profile antivirus up -d

# Redis / MinIO / Traefik / n8n — voir docker-compose.yml (profiles)
```

## Templates démo

Voir [`templates/demo/`](../templates/demo/) et [`template-guide.md`](./template-guide.md).

## Vérifications post-install

1. `docker compose ps` — services healthy  
2. Login UI → dashboard KPIs  
3. Importer `templates/demo/lettre-simple.docx` → générer un document → PDF  
4. Mailpit : envoyer un document depuis le détail  

## Suite

- Admin : [`admin.md`](./admin.md)  
- Sécurité : [`security.md`](./security.md)  
- Backup : [`backup.md`](./backup.md)  
- Dépannage : [`troubleshooting.md`](./troubleshooting.md)  
