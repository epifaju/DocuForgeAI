# Phase 18 — Uninstall (completion notes)

**Completed:** 2026-09-06  
**Scope:** Logical non-destructive uninstall (PRD §§94–96, §151)

## Delivered

| Artifact | Path |
|----------|------|
| API | `DELETE /api/v1/admin/business-packs/{packId}` |
| Response DTO | `PackUninstallResponse` |
| Service | `PackLifecycleService.uninstallPack` |
| Count helper | `GeneratedDocumentRepository.countByCompanyIdAndTemplateSourcePackId` |
| Audit | `AuditActions.PACK_UNINSTALLED` |
| UI | Confirm dialog on pack detail |
| API client | `uninstallBusinessPack` |
| Test | `PackUninstallApiTest` |

## Behaviour

1. Soft uninstall only — never hard-deletes pack / version / template / file rows or storage blobs.
2. Pack status → `UNINSTALLED`; installation → `UNINSTALLED` + `uninstalledAt`.
3. PACK-origin templates → `ARCHIVED` (unavailable for new generations).
4. Historical documents remain GET / downloadable; pack metadata & versions stay queryable.
5. Idempotent if already `UNINSTALLED`; no `force=true`.
6. Enable / template enable blocked while uninstalled (`error.pack.uninstalled`).

## Explicit non-goals

- Physical / cascade delete (`force=true`)
- Export (Phase 19)
- Automatic reinstall of same version after uninstall

## Migrations

- None (statuses / columns already exist from Phases 2–3).

## Verification

```bash
cd backend
mvn -Dtest=PackUninstallApiTest,PackLifecycleApiTest test

cd frontend
npm run lint
npm run build
```
