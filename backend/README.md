# DocuForge AI

Générateur intelligent de documents professionnels (DOCX / PDF) pour TPE/PME.

**Statut :** Phase 2 — Backend Skeleton  
**Mode :** Self-hosted / Docker  
**Langue MVP :** Français  
**Spécification :** voir [`PRD.md`](./PRD.md)

## Stack (imposée)

| Couche | Technologie |
|--------|-------------|
| Backend | Java 21+, Spring Boot, Maven |
| Frontend | React, TypeScript, Vite, Tailwind |
| Base | PostgreSQL |
| PDF | LibreOffice (via JODConverter en Phase 10) |
| IA (optionnelle) | Ollama |
| Email (dev) | Mailpit |

## Prérequis

- Docker Desktop (Windows/macOS) ou Docker Engine + Compose v2+
- JDK 21 + Maven 3.9+ (pour le backend)
- Git

## Démarrage infrastructure

```powershell
Copy-Item .env.example .env
docker compose up -d postgres mailpit ollama libreoffice
powershell -File .\scripts\healthcheck.ps1
```

## Backend (Phase 2)

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
mvn verify
```

Ou via Docker :

```powershell
docker compose up -d --build backend
```

Endpoints publics du squelette :

- `GET /api/v1/ping`
- `GET /actuator/health`
- Swagger UI : `http://localhost:8080/swagger-ui.html`

## Ports hôte (défauts)

| Service | Port hôte | Usage |
|---------|-----------|--------|
| Backend | `8080` | API + Swagger |
| Frontend | `5173` | UI (placeholder) |
| PostgreSQL | `5434` | Base |
| Mailpit UI | `8028` | Emails DEV |
| Mailpit SMTP | `1028` | SMTP hôte |
| Ollama | `11435` | IA locale |
| LibreOffice health | `8081` | Health (dev compose) |

## Configuration

1. Copier `.env.example` vers `.env`
2. Remplacer tous les `changeme_*` hors machine locale
3. Ne jamais committer `.env` ni de secrets réels

## Licence

Voir [`LICENSE`](./LICENSE).