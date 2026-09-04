# Backup / restore

Phase 19 — PRD §§93–94.

Un backup **non restaurable** n’est pas considéré comme valide. Toujours exécuter `verify-backup` après création, et tester un restore sur un environnement de préproduction ou un clone Docker avant de compter sur une sauvegarde en production.

## Contenu d’un backup

Chaque exécution crée un dossier :

```text
backups/docuforge-backup-YYYYMMDD-HHMMSS/
├── MANIFEST.txt
├── CHECKSUMS.sha256
├── db/
│   └── docuforge.sql.gz          # pg_dump (--clean --if-exists)
├── storage.tar.gz                # templates + generated (+ temporary)
└── config/
    ├── env.example
    ├── docker-compose.yml
    ├── docker-compose.dev.yml
    └── env.secrets               # seulement si .env existe (secrets !)
```

| Élément | Source |
|---------|--------|
| PostgreSQL | `pg_dump` dans le conteneur `docuforge-postgres` |
| Templates / documents générés | répertoire hôte `./storage` (bind mount) |
| Configuration | `.env.example`, compose ; copie optionnelle de `.env` |

Variables utiles (`.env`) :

| Variable | Défaut |
|----------|--------|
| `BACKUP_DIR` | `./backups` |
| `STORAGE_HOST_DIR` | `./storage` |
| `POSTGRES_CONTAINER` | `docuforge-postgres` |
| `POSTGRES_*` | mêmes valeurs que Compose |

## Créer un backup

Prérequis : conteneur Postgres démarré (`docker compose up -d postgres`).

**Linux / macOS / Git Bash :**

```bash
chmod +x scripts/backup.sh scripts/verify-backup.sh scripts/restore.sh
./scripts/backup.sh
./scripts/verify-backup.sh ./backups/docuforge-backup-<timestamp>
```

**Windows (PowerShell) :**

```powershell
powershell -File .\scripts\backup.ps1
powershell -File .\scripts\verify-backup.ps1 -BackupDir .\backups\docuforge-backup-<timestamp>
```

Protéger le dossier de backup (permissions, chiffrement hors machine) : `config/env.secrets` contient des secrets si `.env` était présent.

## Restore test (procédure obligatoire)

Scénario documenté par le PRD :

```text
fresh installation
→ restore DB
→ restore storage
→ start
→ verify documents
```

### 1. Fresh installation (cible de restore)

Sur une machine / VM / clone propre :

```bash
git clone <repo> DocuForgeAI && cd DocuForgeAI
cp .env.example .env
# Ajuster POSTGRES_PASSWORD, JWT_SECRET, etc. (ou restaurer config/env.secrets)
docker compose up -d postgres
# Attendre healthy
```

Copier le dossier de backup vers la machine cible (ex. `./backups/docuforge-backup-…`).

### 2. Restore DB + storage

**Bash :**

```bash
./scripts/restore.sh ./backups/docuforge-backup-<timestamp>
# Confirmer avec YES
```

**PowerShell :**

```powershell
powershell -File .\scripts\restore.ps1 -BackupDir .\backups\docuforge-backup-<timestamp>
# ou -Force pour skip confirmation (CI / automation)
```

Le script :

1. Arrête `docuforge-backend` / `docuforge-frontend` si présents  
2. Rejoue le dump SQL (terminaison des sessions puis `psql`)  
3. Remplace `./storage` (l’ancien est déplacé en `storage.pre-restore-<stamp>`)

### 3. Start

```bash
docker compose up -d
./scripts/healthcheck.sh
# Windows: powershell -File .\scripts\healthcheck.ps1
```

### 4. Verify documents

1. Ouvrir l’UI (`APP_BASE_URL`, ex. http://localhost:5174)  
2. Se connecter (compte admin du tenant restauré)  
3. `/documents` — ouvrir / télécharger un DOCX/PDF déjà généré  
4. `/templates` — vérifier qu’un template ACTIVE est listé  
5. Optionnel : régénérer un document depuis un template pour confirmer le pipeline PDF  

Si un fichier référencé en base est absent du storage, le backup n’est **pas** valide : refaire un backup après correction.

## Rollback accidentel

Après restore, l’ancien storage est conservé sous `storage.pre-restore-*`. La base précédente n’est pas snapshotée automatiquement : pour un rollback DB, garder le dump précédent ou un volume Postgres séparé.

## Fichiers

| Script | Rôle |
|--------|------|
| `scripts/backup.sh` / `backup.ps1` | Création |
| `scripts/restore.sh` / `restore.ps1` | Restauration destructive |
| `scripts/verify-backup.sh` / `verify-backup.ps1` | Structure + gzip/tar + checksums |
