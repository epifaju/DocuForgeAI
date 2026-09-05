# Sécurité DocuForge AI

Références PRD : §§16, §34, §38, §75, §90–91. Phase 18 + U0.

## Auth / JWT + cookies (U1)

- Login multi-tenant (`companyIdentifier` + email + password).
- Access JWT HS256 (`type=access`), refresh opaque hashé en base (rotation à chaque refresh).
- **Cookies httpOnly** (`df_access`, `df_refresh`) lorsque `AUTH_COOKIES_ENABLED=true` — le SPA n’écrit plus les tokens dans `localStorage` (Bearer en mémoire + cookie).
- `SameSite=Lax` ; `Secure` forcé si `APP_ENV=production` ou `AUTH_COOKIE_SECURE=true`.
- Fallback API : header `Authorization: Bearer` toujours accepté.
- Reset MDP : `POST /api/v1/auth/forgot-password` + `reset-password` (email SMTP).
- Durées par défaut : access **15 min**, refresh **7 jours**.

## Privacy / RGPD minimal (U1)

- Rétention sociète : setting `gdpr.retentionDays` (UI Paramètres → Confidentialité).
- `GET /api/v1/privacy/export` — ZIP profil + documents de l’utilisateur.
- `DELETE /api/v1/privacy/me` — anonymisation compte + suppression docs personnels.
- `POST /api/v1/privacy/purge` (ADMIN) — purge documents plus anciens que la rétention.
- `DELETE /api/v1/documents/{id}` — suppression document (auteur ou admin).

## Production safety (U0)

Quand `APP_ENV=production` (ou `prod`), `ProductionSafetyValidator` refuse le démarrage si :

- `JWT_SECRET` manquant, moins de 32 caractères, ou placeholder `changeme*`
- mot de passe Postgres placeholder `changeme*`
- `DOCUFORGE_BOOTSTRAP_ENABLED=true`

Scripts :

| Script | Rôle |
|--------|------|
| `scripts/secure-env.* -Prod` / `--prod` | Génère secrets + `APP_ENV=production` + bootstrap off + antivirus on |
| `scripts/verify-prod.*` | Checklist `.env` (TLS, secrets, antivirus) |

## TLS / Traefik (profile `proxy`)

- Entrypoints `:80` → redirect HTTPS, `:443` TLS.
- Mode `TLS_MODE=acme` : Let’s Encrypt (`ACME_EMAIL`, resolver `le`).
- Mode `TLS_MODE=file` : `certs/fullchain.pem` + `privkey.pem` + `docker-compose.tls-file.yml`.
- Override prod : `docker-compose.prod.yml` (pas de ports host app ; accès via Traefik).

## Uploads (§34)

- Noms physiques UUID ; nom original en métadonnée uniquement.
- Validation extension + MIME + taille (`docuforge.storage.*` / multipart).
- Rejet path traversal (`..`, `/`, `\`, null byte) via `StoragePathGuard`.
- Fichiers hors webroot (`STORAGE_ROOT`).
- Scan antivirus **ClamAV** (`ANTIVIRUS_ENABLED=true`, profile `antivirus` + `docker-compose.antivirus.yml`).
  - Malware → `422 MALWARE_DETECTED`
  - ClamAV down → `503 ANTIVIRUS_UNAVAILABLE` (fail-closed)

## Rate limiting (§38)

Bucket4j en mémoire (MVP mono-instance) :

| Endpoint | Variable | Défaut |
|----------|----------|--------|
| `POST /api/v1/auth/login` | `RATE_LIMIT_LOGIN_PER_MINUTE` | 10 |
| `POST /api/v1/ai/**` | `RATE_LIMIT_AI_PER_MINUTE` | 20 |

Réponse `429` / code `RATE_LIMITED`.

## Isolation tenant (§91)

Toutes les lectures/écritures métier filtrent par `company_id` du JWT. Un accès cross-company renvoie `404`.

## Messages d'erreur (i18n)

- Locales : `fr` (défaut), `pt`
- Header `Accept-Language` (envoyé par le frontend selon le sélecteur)
- Fichiers : `backend/src/main/resources/i18n/messages_*.properties`

## Logs (§75)

- JSON structuré (Logback + logstash-encoder).
- MDC : `traceId`, `userId` (après auth).
- Ne pas logger : passwords, JWT, API keys, corps de documents.

## Tests §90

`SecurityHardeningTest` + `AuthSecurityTest` + `LocalStorageProviderTest` + `ProductionSafetyValidatorTest` couvrent :

- API non authentifiée / mauvais rôle
- path traversal / MIME invalide / upload trop gros
- JWT invalide / expiré
- accès cross-company
- rate limit login
- refus démarrage prod avec secrets faibles
