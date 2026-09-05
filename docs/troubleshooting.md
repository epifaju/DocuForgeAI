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

- Bootstrap activé ? `DOCUFORGE_BOOTSTRAP_ENABLED=true` (et variables injectées dans le conteneur)  
- Identifiants : `demo` / `admin@demo.local` / mot de passe `.env` (`DOCUFORGE_BOOTSTRAP_ADMIN_PASSWORD`)  
- Rate limit : attendre 1 minute ou redémarrer le backend  
- Prod : si le backend refuse de démarrer, lire les logs `Production safety` (secrets / bootstrap)

## Backend refuse de démarrer (production)

```text
Refusing to start: production safety checks failed
```

Corriger `JWT_SECRET` / `POSTGRES_PASSWORD`, mettre `DOCUFORGE_BOOTSTRAP_ENABLED=false`, puis `docker compose up -d --force-recreate backend`.

## Traefik / HTTPS

- Profile `proxy` actif ? `docker compose --profile proxy ps`  
- `DOCUFORGE_DOMAIN` doit matcher l’URL du navigateur  
- ACME : ports 80/443 publics + `ACME_EMAIL` valide  
- File TLS : `certs/*.pem` + `infrastructure/traefik/dynamic/tls.yml` + `-f docker-compose.tls-file.yml`  
- Certificat auto-signé : accepter l’exception navigateur (LAN)

## Antivirus / upload rejeté

- `ANTIVIRUS_ENABLED=true` + `--profile antivirus` + `-f docker-compose.antivirus.yml`  
- Premier démarrage ClamAV : attendre le health (~2 min, signatures)  
- ClamAV down → HTTP 503 `ANTIVIRUS_UNAVAILABLE`

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
