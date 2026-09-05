# Sécurité DocuForge AI

Références PRD : §§16, §34, §38, §75, §90–91. Phase 18.

## Auth / JWT

- Login multi-tenant (`companyIdentifier` + email + password).
- Access JWT HS256 (`type=access`), refresh opaque hashé en base (rotation à chaque refresh).
- Durées par défaut : access **15 min**, refresh **7 jours** (`JWT_ACCESS_EXPIRATION` / `JWT_REFRESH_EXPIRATION`).
- UI : stockage access + refresh ; renouvellement **proactif** avant expiration et **retry** sur `401` via `/api/v1/auth/refresh` (une seule requête concurrente).
- Endpoints protégés sauf `login`, `refresh`, health, OpenAPI, ping.
- Secrets : `JWT_SECRET` (≥ 32 octets), jamais loggé ni mis dans `audit_logs.metadata`.

## Uploads (§34)

- Noms physiques UUID ; nom original en métadonnée uniquement.
- Validation extension + MIME + taille (`docuforge.storage.*` / multipart).
- Rejet path traversal (`..`, `/`, `\`, null byte) via `StoragePathGuard`.
- Fichiers hors webroot (`STORAGE_ROOT`).
- Scan antivirus optionnel **ClamAV** (`ANTIVIRUS_ENABLED=true`, profile compose `antivirus`).

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

`SecurityHardeningTest` + `AuthSecurityTest` + `LocalStorageProviderTest` couvrent :

- API non authentifiée / mauvais rôle
- path traversal / MIME invalide / upload trop gros
- JWT invalide / expiré
- accès cross-company
- rate limit login
