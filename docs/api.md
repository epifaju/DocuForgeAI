# API

Base : `http://localhost:18081/api/v1`  
Auth : `Authorization: Bearer <accessToken>`  
Enveloppe succès : `{ "data": … }`

## Auth

| Méthode | Chemin | Description |
|---------|--------|-------------|
| POST | `/auth/login` | `{ companyIdentifier, email, password }` → tokens |
| GET | `/auth/me` | Profil courant |

## Templates

| Méthode | Chemin | Rôles écriture |
|---------|--------|----------------|
| GET/POST | `/templates` | POST : ADMIN, EDITOR |
| GET/PUT/DELETE | `/templates/{id}` | PUT/DELETE : ADMIN, EDITOR |
| POST | `/templates/{id}/versions` | multipart `file` DOCX |
| GET | `/templates/{id}/versions` | |
| POST | `/templates/{id}/activate` \| `/archive` | ADMIN, EDITOR |

Variables : `GET/PUT /template-versions/{id}/variables`  
Formulaire : `GET /template-versions/{id}/form-schema`, `POST …/validate`

## Documents

| Méthode | Chemin |
|---------|--------|
| POST | `/documents/generate` |
| POST | `/documents/preview` |
| GET | `/documents`, `/documents/{id}` | Liste : `q` cherche aussi dans `data_snapshot` (noms / champs formulaire) |
| GET | `/documents/{id}/download/docx` \| `/pdf` (`?preview=true`) |
| POST | `/documents/{id}/new-version` |
| GET | `/documents/{id}/versions` |
| POST | `/documents/{id}/email` | `confirmed: true` obligatoire |

## Batches

`POST /batches` (multipart), `GET /batches`, `GET /batches/{id}`, erreurs + ZIP download.

## Autres

- `GET /dashboard`  
- `GET /audit` (ADMIN, EDITOR)  
- `GET /ai/status`, `POST /ai/rewrite|formalize|summarize|generate`  
- Actuator : `/actuator/health`

OpenAPI / Swagger : si exposé en DEV, typiquement `/swagger-ui.html` (selon config backend).
