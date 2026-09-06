# Phase 20 — Round Trip (completion notes)

**Completed:** 2026-09-06  
**Scope:** Automate export → import → validate → install (PRD §99)

## Delivered

| Artifact | Path |
|----------|------|
| REINSTALL path | `PackInstallationPersistence.reinstallUninstalledVersion` |
| Delete helpers | `deleteByBusinessPackVersionId` on file/prompt/template repos |
| Audit meta | `installationType` on install audit |
| Test | `PackRoundTripApiTest` |

## Behaviour

1. Install custom pack → export ZIP → soft uninstall → re-import exported ZIP → validate `VALID` → install.
2. Same SemVer after `UNINSTALLED` uses `PackInstallationType.REINSTALL` (not conflict).
3. Version children (files / prompts / template links) are refreshed from the ZIP via `StorageProvider` staging.
4. Pack returns to `INSTALLED`; PACK templates become `ACTIVE` again; document generation works.
5. Functional identity asserted: packKey, version, slug, template/prompt/file counts.

## Explicit non-goals

- Artisan demo pack (Phase 21)
- CI workflow extension (§184 — later)
- Hard-delete product API

## Migrations

- None.

## Verification

```bash
cd backend
mvn -Dtest=PackRoundTripApiTest,PackExportApiTest,PackInstallationApiTest,PackUninstallApiTest test
```
