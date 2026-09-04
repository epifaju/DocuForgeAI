# Architecture

Vue d’ensemble MVP — self-hosted Docker.

## Composants

```text
Browser → Frontend (nginx / Vite) → Backend (Spring Boot) → PostgreSQL
                                      ↓
                               Storage bind ./storage
                                      ↓
                         LibreOffice (UNO) ← PDF
                                      ↓
                              Mailpit / SMTP
                                      ↓
                           Ollama (optionnel)
```

| Couche | Techno |
|--------|--------|
| UI | React, TypeScript, Vite, Tailwind |
| API | Java 21, Spring Boot 3.4 |
| DB | PostgreSQL 16 |
| Documents | Apache POI (`{{placeholders}}`) + JODConverter |
| Auth | JWT access + refresh, multi-tenant `company_id` |
| Jobs batch | `@Async` + file PostgreSQL |

## Stockage

```text
storage/
  templates/    # DOCX sources versionnés
  generated/    # DOCX/PDF produits
  temporary/    # travail court
```

`STORAGE_ROOT` dans le conteneur = `/storage` (volume hôte `./storage`).

## Sécurité

- Isolation tenant sur toutes les requêtes métier  
- Rate limiting (login, IA)  
- Antivirus optionnel (ClamAV)  
- Audit trail sans secrets  
- Voir [`security.md`](./security.md)

## Extensions

Profiles Compose : `antivirus`, `cache`, `object-storage`, `automation`, `proxy` — non requis pour le MVP.
