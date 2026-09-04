# Guide administrateur

Opérations courantes après installation — Phase 21.

## Rôles

| Rôle | Capacités principales |
|------|------------------------|
| **ADMIN** | Tout : templates, users, settings, audit, batches, email |
| **EDITOR** | Templates, génération, batches, email, audit |
| **USER** | Génération, documents, email (selon droits API) |
| **VIEWER** | Lecture seule (templates/documents) |

Le compte bootstrap (`DOCUFORGE_BOOTSTRAP_*`) est **ADMIN** du tenant `demo`.

## Templates

Cycle de vie : `DRAFT` → upload version DOCX → `ACTIVE` → `ARCHIVED`.

- UI `/templates` : creation, upload DOCX, activer, archiver (ADMIN/EDITOR)
- Suppression hard uniquement en `DRAFT`
- Un template ACTIVE ayant servi à générer des documents ne doit pas être détruit : **archiver**
- Placeholders : `{{cle.sousCle}}` — voir [`template-guide.md`](./template-guide.md)

## Documents

- Repository `/documents` : filtres, détail, aperçu PDF, téléchargements DOCX/PDF  
- Nouvelle version : `/documents/:id/new-version` (conserve le lignage)  
- Email : confirmation obligatoire ; Mailpit en DEV (`http://localhost:8028`)

## Batch CSV

1. Template **ACTIVE** avec version courante  
2. CSV dont les en-têtes = clés de variables (ex. `client.firstName`)  
3. `/batches` → suivi → ZIP + rapport d’erreurs  

Limite : `BATCH_MAX_ROWS` (défaut 500).

## Audit & dashboard

- `/dashboard` : KPIs du jour / mois, activité récente  
- `/audit` : journal filtrable (ADMIN/EDITOR)  
- Secrets exclus des metadata d’audit  

## Secrets

- Fichier local `.env` uniquement (jamais Git) — générer avec `scripts/secure-env.ps1` / `secure-env.sh`
- Placeholders `changeme_*` dans `.env.example` uniquement
- Backups peuvent contenir `config/env.secrets` — dossier `backups/` gitignoré

## Backup / restore

```bash
./scripts/backup.sh
./scripts/verify-backup.sh ./backups/docuforge-backup-…
# restore destructif — voir docs/backup.md
```

Windows : `scripts/backup.ps1`, `restore.ps1`, `verify-backup.ps1`.

## IA (optionnelle)

`AI_ENABLED=false` par défaut. Activer Ollama + `AI_ENABLED=true` n’est **jamais** requis pour générer des documents.

## Maintenance

| Action | Commande |
|--------|----------|
| Logs backend | `docker logs -f docuforge-backend` |
| Rebuild backend | `docker compose build backend && docker compose up -d backend` |
| Rebuild frontend | `docker compose build frontend && docker compose up -d frontend` |
| Arrêt | `docker compose down` (volumes conservés) |

## Users (ADMIN)

- UI `/users` — creer un utilisateur, activer/desactiver
- API `GET/POST /api/v1/admin/users`, `PUT /api/v1/admin/users/{id}`

## Settings societe (ADMIN)

- UI `/settings` (sections `/settings/company`, `/ai`, `/email` ; `/settings/users` → `/users`)
- API `GET/PUT /api/v1/admin/settings`
- Nom societe editable ; identifiant de connexion en lecture seule
- Toggle IA par societe (en plus de `AI_ENABLED` plateforme)
- Expediteur email override (sinon `SMTP_FROM`)
- Audit `SETTINGS_CHANGED`
