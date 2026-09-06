# Phase 10 — Installation Engine (completion notes)

**Completed:** 2026-09-06  
**Scope:** Transactional `PackInstallationService` from a `VALID` import job — pack / version / templates / variables / prompts / files / installation (PRD §§72–74, §82)

## Delivered

| Artifact | Path |
|----------|------|
| Orchestrator | `PackInstallationService` — stage via `StorageProvider`, compensate on failure |
| Persistence TX | `PackInstallationPersistence` — `@Transactional` DB boundary (separate bean to avoid self-invocation) |
| DTOs | `PackInstallRequest`, `PackInstallResponse` |
| API | `POST /api/v1/admin/business-packs/imports/{jobId}/install` |
| Audit | `PACK_INSTALLED`, `PACK_INSTALLATION_FAILED` |
| Tests | `PackInstallationApiTest` |

## API

| Method | Path | Notes |
|--------|------|--------|
| `POST` | `/api/v1/admin/business-packs/imports/{jobId}/install` | Body `{ enablePack, enableTemplates }` (defaults `true`); requires job `VALID`; ADMIN; company-scoped |

Response includes `packId`, `packVersionId`, `installationId`, counts, `FRESH` / `UPDATE`.

## Installation pipeline

```text
VALID job
  → INSTALLING
  → read staging ZIP (StorageProvider)
  → stage pack assets (templates → TEMPLATES, others → TEMPORARY)
  → @Transactional persist (pack, version, templates, variables, prompts, files, installation)
  → INSTALLED + audit
```

On failure: compensate staged keys, job → `FAILED`, audit `PACK_INSTALLATION_FAILED`, DB rollback.

Guards: version already installed, SemVer downgrade, USER/other-pack template code collision. Idempotent if job already `INSTALLED`.

## Explicit non-goals

- List / detail query API (Phase 11)
- Enable / disable pack or template APIs (Phase 12)
- Uninstall / update / export
- Frontend wizard
- SKIP LOCKED async install worker

## Migrations

- **None** (V9–V11 entities/columns reused). V1–V11 untouched.

## Verification

```bash
cd backend
mvn "-Dtest=PackInstallationApiTest,PackImportApiTest,SchemaMigrationTest,BusinessPackDomainTest,BusinessPackRelationsTest" test
```
