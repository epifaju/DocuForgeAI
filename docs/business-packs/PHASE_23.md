# Phase 23 — E2E (completion notes)

**Completed:** 2026-09-06  
**Scope:** Playwright automation of PRD §§188–189 (principal + update)

## Delivered

| Artifact | Path |
|----------|------|
| Principal E2E | `tests/e2e/specs/06-business-pack.spec.ts` |
| Update E2E | `tests/e2e/specs/07-business-pack-update.spec.ts` |
| Pack helpers | `tests/e2e/helpers/packs.ts` |
| Authed navigation | `tests/e2e/helpers/auth.ts` → `gotoAuthed` |
| npm script | `tests/e2e` → `npm run test:packs` |

## Scenarios

### §188 Principal

Login ADMIN → Packs métier → import `docuforge-pack-artisan-demo-1.0.0.zip` → VALID → install → pack visible → Templates / `ARTISAN_DEVIS` → generate → DOCX/PDF download → document `COMPLETED`.

### §189 Update

Install 1.0.0 → generate historical doc → import materialised 1.1.0 ZIP → review / update → generate again → reopen old document (`COMPLETED` preserved).

## Runtime notes

- Default Playwright base URL: `http://127.0.0.1:5175` (Vite) so an outdated Docker frontend on `:5174` is not reused.
- Backend must expose pack APIs (Flyway ≥ V11). Rebuild: `docker compose build backend && docker compose up -d backend`.
- Prefer SPA clicks over full `page.goto` when possible (in-memory access token).
- Update E2E picks the next free `1.N.0` via API (`nextFreeArtisanMinorVersion`) so shared Docker DBs with historical versions do not collide.

## Explicit non-goals

- Creator/admin prose guides (Phase 24)
- Release report / full CI matrix (Phase 25)
- Frontend unit suite for pack list/wizard (§187) beyond Playwright

## Migrations

- None.

## Verification

```bash
# Backend up-to-date on :18081, then:
cd tests/e2e
npm install
npx playwright install chromium
npm run test:packs
```
