# Phase 16 — Update UI (completion notes)

**Completed:** 2026-09-06  
**Scope:** Frontend update review — old/new version, diff, warnings, breaking changes, confirmation (PRD §122 / Phase 16)

## Delivered

| Artifact | Path |
|----------|------|
| Preview panel | `frontend/src/components/PackUpdatePreviewPanel.tsx` |
| API client | `getPackUpdatePreview` on `businessPacks.ts` |
| Wizard review | Update branch in `BusinessPackImportPage.tsx` |
| Detail banner | `UPDATE_AVAILABLE` CTA → import wizard |
| i18n | `packs.import.update.*` + `packs.updateAvailable*` FR/PT |

## Flow

1. Import + validate a higher SemVer of an installed pack (Phase 14/15).
2. Review step calls `GET .../imports/{jobId}/update-preview`.
3. Panel shows `installed → candidate`, SemVer kind, summary counts, breaking list, change list.
4. Primary action **Mettre à jour** → ConfirmDialog (danger if breaking).
5. Install uses existing `POST .../install` (`UPDATE` / `PACK_UPDATED`).
6. Success copy distinguishes FRESH vs UPDATE.
7. Pack detail shows banner when status is `UPDATE_AVAILABLE`.

State: TanStack Query + local React state (no Zustand).

## Explicit non-goals

- Duplicate pack template (Phase 17)
- Uninstall / export
- Zustand introduction
- Backend API changes
- E2E Playwright

## Migrations

- None.

## Verification

```bash
cd frontend
npm run lint
npm run build
```
