# Phase 22 — Security Hardening (completion notes)

**Completed:** 2026-09-06  
**Scope:** Archive + API security campaign (PRD Phase 22, §§141–142, acceptance §190)

## Delivered

| Artifact | Path |
|----------|------|
| ZIP magic at upload | `PackImportService.looksLikeZip` |
| Canonical entry keys | `PackArchiveInspector.normalizeEntryKey` |
| Unit / inspector tests | `PackArchiveInspectorTest`, `PackImportServiceZipMagicTest` |
| API campaign | `PackSecurityHardeningApiTest` |

## Campaign coverage

| Threat | Defence |
|--------|---------|
| ZIP Slip (`../`, absolute, Windows) | `PackArchiveInspector` → `PACK_UNSAFE_PATH` |
| ZIP bomb / ratio / entry caps | Existing inspector limits |
| Malformed / empty ZIP | `PACK_ARCHIVE_INVALID` |
| Nested ZIP / `.docm` / exe | `PACK_UNSUPPORTED_FILE_TYPE` |
| Duplicate / `./` alias duplicates | Canonical name set |
| Null-byte entry name | `PACK_UNSAFE_PATH` |
| MIME spoof (`.zip` without PK header) | Reject at upload |
| Filename path traversal | `StoragePathGuard` → `PATH_TRAVERSAL` |
| Oversized upload | Pack max + HTTP 413 |
| Malformed DOCX | Existing `PackTemplateValidatorTest` |
| RBAC (VIEWER) | `@PreAuthorize ADMIN` → 403 |
| Tenant isolation | Company-scoped jobs → 404 |

## Explicit non-goals

- E2E Playwright (Phase 23)
- Creator/admin security guide prose (Phase 24)
- ClamAV policy changes
- New migrations

## Migrations

- None.

## Verification

```bash
cd backend
mvn -Dtest="PackArchiveInspectorTest,PackImportServiceZipMagicTest,PackSecurityHardeningApiTest,PackImportApiTest,PackTemplateValidatorTest" test
```
