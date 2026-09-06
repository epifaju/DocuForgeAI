# Phase 12 — Enable / Disable (completion notes)

**Completed:** 2026-09-06  
**Scope:** ADMIN pack + pack-template enable/disable with history preserved (PRD §§87–89, §149)

## Delivered

| Artifact | Path |
|----------|------|
| Controller | `PackLifecycleController` |
| Service | `PackLifecycleService` |
| Audit | `PACK_ENABLED`, `PACK_DISABLED`, `PACK_TEMPLATE_ENABLED`, `PACK_TEMPLATE_DISABLED` |
| Tests | `PackLifecycleApiTest` (incl. historical document access) |

## API

| Method | Path | Effect |
|--------|------|--------|
| `POST` | `/api/v1/admin/business-packs/{packId}/enable` | Pack `INSTALLED`, installation `ACTIVE`, restore cascaded templates |
| `POST` | `/api/v1/admin/business-packs/{packId}/disable` | Pack `DISABLED`, installation `DISABLED` + `disabledAt`, cascade `ACTIVE`→`DRAFT` |
| `POST` | `.../templates/{templateCode}/enable` | Template `ACTIVE` (pack must not be DISABLED) |
| `POST` | `.../templates/{templateCode}/disable` | Template `DRAFT` |

All routes: `@PreAuthorize("hasRole('ADMIN')")`, company-scoped.

## History preservation

- No row deletion; versions / templates / files / prompts kept
- Pack disable cascades only currently `ACTIVE` pack templates to `DRAFT`; IDs stored in installation `metadata.cascadedTemplateIds` for restore
- Individually disabled templates (already `DRAFT`) are **not** re-activated on pack enable
- Existing generated documents remain GET/downloadable; new generation blocked while template is non-`ACTIVE`

## Explicit non-goals

- Uninstall / update / export
- Frontend (Phase 13)
- Changing generic `/templates/{id}/activate|archive` APIs

## Migrations

- **None**. V1–V11 untouched.

## Verification

```bash
cd backend
mvn "-Dtest=PackLifecycleApiTest,PackQueryApiTest,PackInstallationApiTest,SchemaMigrationTest" test
```
