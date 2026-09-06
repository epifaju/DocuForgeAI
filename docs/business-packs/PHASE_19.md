# Phase 19 — Export (completion notes)

**Completed:** 2026-09-06  
**Scope:** PackExportService — ZIP DBPF-1 valide, checksums recalculés (PRD §§97–98, §152)

## Delivered

| Artifact | Path |
|----------|------|
| Service | `PackExportService` |
| Download DTO | `PackExportDownload` |
| API | `GET` / `POST` `/api/v1/admin/business-packs/{packId}/export` |
| Controller | `PackExportController` |
| Audit | `AuditActions.PACK_EXPORTED` |
| UI | Export button on pack detail |
| API client | `exportBusinessPack` |
| Test | `PackExportApiTest` |

## Behaviour

1. Exports the **current** pack version as `application/zip`.
2. Reads all `business_pack_files` (except stored `manifest.json`) via `StorageProvider`.
3. Recalculates SHA-256 checksums (`sha256:<hex>`) from live bytes.
4. Rebuilds `manifest.json` from the stored snapshot with updated `checksums`.
5. ZIP structure: `manifest.json` + templates / metadata / prompts / samples / previews / assets.
6. Audit metadata: packKey, version, fileCount, archiveBytes (no archive body).
7. Works for INSTALLED / DISABLED / UNINSTALLED (soft-preserved storage).

## Explicit non-goals

- Artisan demo pack (Phase 21)
- CI workflow extension (§184 — later)
- Official marketplace publishing

## Migrations

- None.

## Verification

```bash
cd backend
mvn -Dtest=PackExportApiTest test

cd frontend
npm run lint
npm run build
```
