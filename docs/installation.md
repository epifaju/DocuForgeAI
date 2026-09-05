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

Désactiver le bootstrap en production : `DOCUFORGE_BOOTSTRAP_ENABLED=false` après création du premier admin. Les variables `DOCUFORGE_BOOTSTRAP_*` sont injectées dans le conteneur backend via Compose.

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
# Antivirus ClamAV (attendre healthy ~2 min au premier démarrage)
ANTIVIRUS_ENABLED=true docker compose -f docker-compose.yml -f docker-compose.antivirus.yml \
  --profile antivirus up -d

# Redis / MinIO / Traefik / n8n — voir docker-compose.yml (profiles)
```

## Templates démo

Voir [`templates/demo/`](../templates/demo/) et [`template-guide.md`](./template-guide.md).

## Déploiement HTTPS (U0 / production-like)

Parcours recommandé après création du premier admin :

```bash
# 1) Secrets + flags prod
./scripts/secure-env.sh --prod --show          # ou secure-env.ps1 -Prod -Show

# 2) Domaine + URL
# Éditer .env :
#   DOCUFORGE_DOMAIN=docs.example.com
#   APP_BASE_URL=https://docs.example.com
#   TLS_MODE=acme | file
#   ACME_EMAIL=you@example.com   # si TLS_MODE=acme

# 3) Vérification
./scripts/verify-prod.sh                       # ou verify-prod.ps1

# 4) Stack (ports host retirés ; entrée via Traefik 80/443)
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  -f docker-compose.antivirus.yml \
  --profile antivirus --profile proxy up -d
```

**TLS fichiers (LAN / on-prem)** :

```bash
./scripts/gen-dev-certs.sh docs.local   # ou gen-dev-certs.ps1
# .env : TLS_MODE=file , TRAEFIK_CERT_RESOLVER= (vide)
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  -f docker-compose.antivirus.yml -f docker-compose.tls-file.yml \
  --profile antivirus --profile proxy up -d
```

**Premier admin en prod** : démarrer une fois avec `DOCUFORGE_BOOTSTRAP_ENABLED=true` + mots de passe forts, se connecter, puis repasser à `false` et `docker compose up -d --force-recreate backend`. Avec `APP_ENV=production`, le backend **refuse** de démarrer si secrets faibles ou bootstrap encore activé.

L’UI derrière Traefik proxifie `/api` via nginx frontend (same-origin). Voir aussi [`security.md`](./security.md).

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
