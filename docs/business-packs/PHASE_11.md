# Phase 11 — Pack Query API (completion notes)

**Completed:** 2026-09-06  
**Scope:** ADMIN read APIs — list / detail / versions / templates with pagination & RBAC (PRD §§83–86)

## Delivered

| Artifact | Path |
|----------|------|
| Controller | `PackQueryController` |
| Service | `PackQueryService` |
| Mapper | `PackQueryMapper` |
| DTOs | `PackSummaryResponse`, `PackDetailResponse`, `PackVersionResponse`, `PackTemplateResponse`, `PackPromptResponse`, `PackInstallationResponse` |
| Repo | `BusinessPackRepository.search` / `findByIdAndCompanyIdWithCurrentVersion` |
| Tests | `PackQueryApiTest` |

## API

| Method | Path | Notes |
|--------|------|--------|
| `GET` | `/api/v1/admin/business-packs` | Filters: `status`, `type`, `search`, `page`, `size`, `sort` (whitelist); `PageResponse` |
| `GET` | `/api/v1/admin/business-packs/{packId}` | Metadata + current version + versions + templates + prompts + installation |
| `GET` | `/api/v1/admin/business-packs/{packId}/versions` | All versions (`current` flag) |
| `GET` | `/api/v1/admin/business-packs/{packId}/templates` | Templates of **current** pack version |

All routes: `@PreAuthorize("hasRole('ADMIN')")`, company-scoped, feature flag `docuforge.packs.enabled`.

`sort` format: `field` or `field,asc|desc`. Allowed fields: `name`, `packKey`, `slug`, `status`, `packType`, `createdAt`, `updatedAt` (default `updatedAt,desc`).

## Explicit non-goals

- Enable / disable pack or template (Phase 12)
- Frontend list/detail (Phase 13)
- Global/`company_id` NULL catalog packs in list (company-local installs only)
- Manifest JSON on version list payloads (omitted; stored on entity)

## Migrations

- **None** (V9–V11 reused). V1–V11 untouched.

## Verification

```bash
cd backend
mvn "-Dtest=PackQueryApiTest,PackInstallationApiTest,PackImportApiTest,SchemaMigrationTest" test
```
