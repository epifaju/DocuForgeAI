# Phase 9 — Import API (completion notes)

**Completed:** 2026-09-06  
**Scope:** ADMIN import upload + status + sync validate + staging via `StorageProvider` + retention + audit (no install)

## Delivered

| Artifact | Path |
|----------|------|
| Controller | `PackImportController` `/api/v1/admin/business-packs` |
| Service | `PackImportService` |
| Retention | `PackImportRetentionScheduler` |
| DTO | `PackImportJobResponse` |
| Migration | `V11__pack_import_staging.sql` (`staging_storage_key`, `expires_at`) |
| Storage category | `StorageCategory.PACK_IMPORTS` |
| Audit | `PACK_UPLOADED`, `PACK_VALIDATED`, `PACK_VALIDATION_FAILED` |
| Tests | `PackImportApiTest` |

## API

| Method | Path | Notes |
|--------|------|--------|
| `POST` | `/api/v1/admin/business-packs/import` | multipart `file` → staging, status `UPLOADED` (202) |
| `GET` | `/api/v1/admin/business-packs/imports/{jobId}` | status + validation report + pack metadata |
| `POST` | `/api/v1/admin/business-packs/imports/{jobId}/validate` | sync `PackValidationService` → `VALID` / `INVALID` |

All routes: `@PreAuthorize("hasRole('ADMIN')")`, company-scoped.

## Staging & retention

- Archive stored under `packimports/` via `StorageProvider` (antivirus on store; key shape matches `StoragePathGuard`)
- `expires_at = now + docuforge.packs.import-retention-hours`
- Hourly sweep marks eligible jobs `EXPIRED` and deletes staging object

## Explicit non-goals

- `POST .../install` (Phase 10)
- List packs / enable-disable APIs
- Frontend wizard
- SKIP LOCKED async worker (sync validate for MVP; job columns ready)

## Migrations

- **V11** only (V1–V10 untouched)

## Verification

```bash
cd backend
mvn "-Dtest=PackImportApiTest,SchemaMigrationTest,PackValidationServiceTest" test
```
