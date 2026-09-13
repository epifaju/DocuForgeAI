# Déploiement Windows 11 — Docker Desktop (WSL2)

Guide self-host reproductible pour DocuForge AI.  
Complète [`docs/installation.md`](docs/installation.md) et [`docs/backup.md`](docs/backup.md) avec un parcours orienté Windows.

> **Client final (consulat / non technique)** : installeur graphique [`installer/README.md`](installer/README.md)  
> (wizard Inno Setup + barre système, runtime **Rancher Desktop** — sans licence Docker Desktop).  
> Y compris : lancement après install, URL, connexion admin, pack images / Rancher offline, FAQ build.

## 1. Prérequis

| Élément | Recommandation |
|--------|----------------|
| OS | Windows 11 (ou Windows 10 22H2+) |
| Docker Desktop | **4.30+** (Compose V2 intégré) |
| Backend Docker | **WSL2** (pas Hyper-V legacy) |
| Ressources Docker | ≥ **4 Go RAM** alloués (6–8 Go si LibreOffice + Ollama) ; ≥ 2 CPU |
| Disque | ≥ **10 Go** libres (images + volume Postgres + storage) |
| Outils | Git, PowerShell 5.1+ (ou 7+) |

Activer WSL2 dans Docker Desktop : **Settings → General → Use the WSL 2 based engine**.

Ports hôte libres par défaut : `5174` (UI), `18081` (API), `5434` (Postgres), `8028`/`1028` (Mailpit).  
Ollama (`11435`) seulement si le profil `ai` est activé.

## 2. Installation (self-host léger)

Depuis PowerShell, à la racine du dépôt cloné :

```powershell
git clone <url-du-repo> DocuForgeAI
cd DocuForgeAI

Copy-Item .env.example .env
notepad .env   # ou votre éditeur
```

### Variables à changer avant le premier démarrage

| Variable | Rôle |
|----------|------|
| `POSTGRES_PASSWORD` | Mot de passe Postgres |
| `JWT_SECRET` | Secret JWT (≥ 32 caractères) |
| `DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD` | Mot de passe du premier admin |
| `APP_BASE_URL` | URL d’accès UI (`http://localhost:5174` ou IP LAN) |
| `STORAGE_VOLUME` | `storage_data` (défaut, volume nommé — recommandé WSL2) |

Génération rapide de secrets :

```powershell
powershell -File .\scripts\secure-env.ps1 -Show
# Pour un démarrage « production-like » (refuse secrets faibles) :
powershell -File .\scripts\secure-env.ps1 -Prod -Show
```

### Démarrage

**Développement / essai local** (bootstrap activé, profil Spring `docker`) :

```powershell
docker compose up -d --build
docker compose ps
```

**Self-host LAN « production-like »** (sans Traefik, ports hôte conservés) :

```powershell
# Premier boot uniquement : dans .env laisser DOCUFORGE_BOOTSTRAP_ENABLED=true
# puis après création de l'admin : false + recreate backend
docker compose -f docker-compose.yml -f docker-compose.selfhost.yml up -d --build
docker compose ps
```

Vérifications :

```powershell
docker compose ps
# postgres, backend, frontend, libreoffice, mailpit → healthy / running

# Health API
Invoke-WebRequest -Uri http://127.0.0.1:18081/actuator/health -UseBasicParsing

# UI
Start-Process http://localhost:5174
```

Script d’install existant (équivalent) :

```powershell
powershell -File .\scripts\install.ps1
```

### Première connexion (bootstrap)

| Champ | Valeur |
|-------|--------|
| Société | `demo` (`DOCUFORGE_BOOTSTRAP_COMPANY_IDENTIFIER`) |
| Email | `admin@demo.local` |
| Mot de passe | valeur de `DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD` |

Puis désactiver le bootstrap : `DOCUFORGE_BOOTSTRAP_ENABLED=false` dans `.env`, puis :

```powershell
docker compose up -d --force-recreate backend
```

## 3. Ollama (IA) — optionnel

Ollama **n’est pas démarré** par défaut (profil Compose `ai`).  
Avec `AI_ENABLED=false` (défaut), l’application fonctionne (templates, génération DOCX/PDF, lots CSV) sans IA.

### Activer

1. Dans `.env` :

```env
AI_ENABLED=true
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=llama3.2
# Option : activer le profil automatiquement
COMPOSE_PROFILES=ai
```

2. Démarrer avec le profil :

```powershell
docker compose --profile ai up -d
# ou, si COMPOSE_PROFILES=ai est déjà dans .env :
docker compose up -d
```

3. Télécharger un modèle (première fois) :

```powershell
docker exec -it docuforge-ollama ollama pull llama3.2
```

### Désactiver

```powershell
# .env
AI_ENABLED=false
# retirer COMPOSE_PROFILES=ai si présent

docker compose --profile ai stop ollama
# ou redémarrer sans le profil :
docker compose up -d --remove-orphans
```

Le backend **ne dépend pas** d’Ollama (`depends_on` volontairement absent) : une panne IA ne bloque pas le cœur métier.

## 4. Migrations Flyway

Automatiques au démarrage du backend (Spring Boot + Flyway).  
**Aucune action manuelle** n’est requise au premier boot ni après une mise à jour d’image backend, tant que le volume Postgres est conservé.

En cas d’échec de migration : `docker compose logs backend` et ne pas supprimer le volume Postgres sans backup.

## 5. Persistance & volumes (WSL2)

| Données | Volume / chemin | Notes |
|---------|-----------------|-------|
| PostgreSQL | volume nommé `postgres_data` | Persistant entre `compose down` (sans `-v`) |
| Fichiers (templates, PDF, DOCX) | `STORAGE_VOLUME` → défaut `storage_data` | Volume nommé = meilleurs perfs sous WSL2 |
| Modèles Ollama | `ollama_data` | Uniquement si profil `ai` |

Éviter de monter de **gros** arbres depuis `C:\...` en bind mount pour le storage en prod : privilégier `STORAGE_VOLUME=storage_data`.

### Migration depuis un ancien bind `./storage`

Si vous aviez déjà des fichiers dans `.\storage` (compose antérieur) :

1. Soit conserver le bind : `STORAGE_VOLUME=./storage` dans `.env`
2. Soit copier vers le volume nommé puis basculer :

```powershell
docker compose up -d backend
docker run --rm -v "${PWD}/storage:/from:ro" -v docuforge-ai_storage_data:/to alpine `
  sh -c "cp -a /from/. /to/"
# puis STORAGE_VOLUME=storage_data et recreate backend
```

Les scripts `scripts/backup.ps1` / `restore.ps1` attendent aujourd’hui un répertoire hôte (`STORAGE_HOST_DIR=./storage`).  
Pour les utiliser tels quels :

```env
STORAGE_VOLUME=./storage
STORAGE_HOST_DIR=./storage
```

puis `docker compose up -d --force-recreate backend`.

## 6. Sauvegarde / restauration PostgreSQL

### Dump (sans arrêter toute la stack)

```powershell
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
New-Item -ItemType Directory -Force -Path .\backups | Out-Null
docker exec docuforge-postgres pg_dump -U docuforge -d docuforge --clean --if-exists |
  Out-File -Encoding utf8 ".\backups\docuforge-$stamp.sql"
# Compresser si besoin (tar / 7zip)
```

Ou via le script projet (si `STORAGE_VOLUME=./storage`) :

```powershell
powershell -File .\scripts\backup.ps1
```

### Restore DB

```powershell
# Exemple : restaurer un dump SQL
Get-Content .\backups\docuforge-YYYYMMDD-HHMMSS.sql -Raw |
  docker exec -i docuforge-postgres psql -U docuforge -d docuforge
```

Procédure complète (DB + storage + verify) : [`docs/backup.md`](docs/backup.md).

### Backup du volume nommé `storage_data`

```powershell
docker run --rm `
  -v docuforge-ai_storage_data:/storage:ro `
  -v ${PWD}/backups:/backups `
  alpine tar czf /backups/storage-$stamp.tar.gz -C /storage .
```

(Le préfixe de volume peut être `docuforge-ai_` selon le nom du projet Compose — vérifier avec `docker volume ls`.)

## 7. Mise à jour de l’application

```powershell
git pull
docker compose build
docker compose up -d
docker compose ps
docker compose logs backend --tail 80
```

Avec self-host :

```powershell
docker compose -f docker-compose.yml -f docker-compose.selfhost.yml up -d --build
```

- Flyway applique les nouvelles migrations au boot.
- **Ne pas** lancer `docker compose down -v` (le `-v` efface Postgres + storage).
- Après maj majeure, vérifier login + génération d’un document démo.

## 8. HTTPS (optionnel, Traefik)

Parcours documenté dans [`docs/installation.md`](docs/installation.md) § Déploiement HTTPS :

```powershell
powershell -File .\scripts\secure-env.ps1 -Prod -Show
powershell -File .\scripts\verify-prod.ps1
docker compose -f docker-compose.yml -f docker-compose.prod.yml `
  --profile proxy up -d
```

Les ports UI/API hôte sont alors retirés ; l’entrée se fait via 80/443.

## 9. Dépannage Windows

### Ports déjà utilisés

```powershell
netstat -ano | findstr ":5174"
netstat -ano | findstr ":18081"
netstat -ano | findstr ":5434"
```

Changer `FRONTEND_PORT` / `BACKEND_PORT` / `POSTGRES_PORT` dans `.env`, puis `docker compose up -d`.

### Docker Desktop ne démarre pas / WSL2

- Redémarrer le service WSL : `wsl --shutdown` puis relancer Docker Desktop  
- Vérifier **Settings → Resources → WSL Integration**  
- Mettre à jour WSL : `wsl --update`

### Backend unhealthy

```powershell
docker compose logs -f backend
docker compose logs libreoffice --tail 50
```

Points fréquents : secrets trop faibles en `APP_ENV=production`, Postgres pas ready, LibreOffice long au premier start (`start_period` ~90s).

### Frontend OK mais API en erreur

Le nginx du frontend proxifie `/api/` vers `backend:8080`.  
Vérifier `docker compose ps backend` et les logs nginx / backend.

### Ollama / IA indisponible

Normal si le profil `ai` n’est pas actif. Sinon : `docker compose --profile ai ps`, `docker exec docuforge-ollama ollama list`.

### Logs utiles

```powershell
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
docker compose logs -f libreoffice
docker compose --profile ai logs -f ollama
```

Plus de cas : [`docs/troubleshooting.md`](docs/troubleshooting.md).

## 10. Architecture des services

| Service | Obligatoire | Rôle |
|---------|-------------|------|
| `postgres` | oui | Données applicatives |
| `backend` | oui | API Spring Boot + Flyway |
| `frontend` | oui | UI React (nginx, proxy `/api`) |
| `libreoffice` | oui* | Conversion PDF (*si `PDF_CONVERSION_ENABLED=true`) |
| `mailpit` | oui (stack défaut) | SMTP de démo / capture mails |
| `ollama` | non | IA locale (`--profile ai`) |
| `clamav` / `traefik` / … | non | Autres profils Compose |

## 11. Fichiers Docker utiles

| Fichier | Usage |
|---------|--------|
| `docker-compose.yml` | Stack de base |
| `docker-compose.selfhost.yml` | Prod-like LAN / Windows sans Traefik |
| `docker-compose.prod.yml` | Prod HTTPS (retrait ports + Traefik) |
| `docker-compose.dev.yml` | Dev (bind `./storage`) |
| `.env.example` | Modèle de configuration |
| `backend/Dockerfile` | Multi-stage Maven → JRE 21 |
| `frontend/Dockerfile` | Multi-stage Vite → nginx |
