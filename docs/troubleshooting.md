# Dépannage

## Backend unhealthy / 404 sur de nouveaux endpoints

L’image Docker est peut‑être obsolète.

```bash
docker compose build backend
docker compose up -d backend
docker logs docuforge-backend --tail 50
```

Chercher : `JODConverter connected to LibreOffice UNO`.

## Dashboard / génération PDF : « aucun convertisseur disponible »

Le bean PDF ne démarre pas ou LibreOffice est down.

```bash
docker compose ps libreoffice
docker compose restart libreoffice backend
```

## Formulaire : clic « Générer » sans effet

Vérifier que le frontend inclut le correctif des clés `client.firstName` (Phase 20). Rebuild :

```bash
docker compose build frontend && docker compose up -d frontend
# ou: cd frontend && npm run dev
```

## Ports déjà utilisés

Modifier `.env` (`BACKEND_PORT`, `FRONTEND_PORT`, `POSTGRES_PORT`, …) puis `docker compose up -d`.

## Login échoue

- Bootstrap activé ? `DOCUFORGE_BOOTSTRAP_ENABLED=true`  
- Identifiants : `demo` / `admin@demo.local` / mot de passe `.env`  
- Rate limit : attendre 1 minute ou redémarrer le backend  

## Email invisible

Mailpit UI : http://localhost:8028 — vérifier `SMTP_HOST=mailpit` dans le réseau Compose.

## Batch en échec

- Template ACTIVE + version courante  
- En-têtes CSV = clés exactes des variables  
- Logs : `docker logs docuforge-backend`  

## Backup

```bash
./scripts/verify-backup.sh ./backups/docuforge-backup-…
```

Un backup non restaurable n’est pas valide — voir [`backup.md`](./backup.md).

## E2E

Voir [`e2e.md`](./e2e.md). Préférer `localhost` (pas seulement `127.0.0.1`) sous Windows si le frontend écoute en IPv6.
