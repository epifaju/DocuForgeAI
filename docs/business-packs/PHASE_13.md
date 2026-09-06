# Phase 13 — Frontend List & Detail (completion notes)

**Completed:** 2026-09-06  
**Scope:** ADMIN Packs list + detail (Overview / Templates / Versions tabs), TanStack Query, loading/error/empty (PRD §§109–111, Phase 13)

## Delivered

| Artifact | Path |
|----------|------|
| API client | `frontend/src/api/businessPacks.ts` |
| List page | `frontend/src/pages/BusinessPacksPage.tsx` |
| Detail + tabs | `frontend/src/pages/BusinessPackDetailPage.tsx` |
| Nav | `AppShell` ADMIN → Packs metier |
| i18n | `fr.json` / `pt.json` `nav.packs` + `packs.*` |
| StatusBadge | tones for pack statuses / types |

## Routes (UI style aligned with `/users`)

| Path | Page |
|------|------|
| `/business-packs` | List + filters (status, type, search) + enable/disable |
| `/business-packs/:packId` | Overview |
| `/business-packs/:packId/templates` | Templates tab |
| `/business-packs/:packId/versions` | Versions tab |

PRD literal `/admin/business-packs` mapped to flat `/business-packs` (same convention as `/users` vs API `/api/v1/admin/users`).

## Explicit non-goals

- Import wizard (Phase 14)
- Zustand (not in stack; list filters use URL search params + local state)
- E2E Playwright pack suite (no frontend unit test runner)

## Migrations

- None (frontend only).

## Verification

```bash
cd frontend
npm run lint
npm run build
```
