# Business Pack System — Release Report (Phase 25)

**Date:** 2026-09-06  
**Module:** DocuForge Business Pack System (DBPF-1)  
**PRD:** `docs/PRD_BUSINESS_PACK_SYSTEM.md` — Phase 25  
**Scope:** Release readiness verification for phases 1–24 deliverables

## Verdict

**READY FOR MVP RELEASE** of the Business Pack System on the current DocuForge stack (Java 21 / Spring Boot 3.4.4, React 19, PostgreSQL, Flyway **V1–V11**), subject to the known limitations below.

Out of scope (PRD §6): marketplace, payments, PKI signatures, remote auto-update.

## Verification matrix

| Check | Command / method | Result |
|-------|------------------|--------|
| Backend build + unit + integration | `cd backend && mvn -B verify` | **PASS** — 195 tests, 0 failures, 0 errors |
| Frontend lint | `cd frontend && npm run lint` | **PASS** |
| Frontend build | `cd frontend && npm run build` | **PASS** (chunk size warning only) |
| Pack security suite | Included in `mvn verify` (`PackSecurityHardeningApiTest`, archive/MIME unit tests) | **PASS** |
| Pack E2E (§188 / §189) | `cd tests/e2e && npm run test:packs` | **PASS** — 2/2 |
| Docker Compose config | Same overlays as `.github/workflows/ci.yml` | **PASS** (exit 0 × 4) |
| Flyway | Testcontainers apply V1–V11; `SchemaMigrationTest` | **PASS** — schema at **v11** |
| Secret scan | Light `rg` for common key patterns (no gitleaks in repo) | **PASS** — no hits |
| CI alignment | `.github/workflows/ci.yml` | Backend `mvn verify`, FE lint/build, compose config |

## Fixes applied during Phase 25

| Item | Why |
|------|-----|
| `PingControllerTest` → `@MockitoBean ErrorMessages` | `@WebMvcTest` failed to load context after `GlobalExceptionHandler` gained i18n dependency (blocked full `mvn verify`) |
| E2E update isolation (`nextFreeArtisanMinorVersion`) | Shared Docker DB retained historical pack versions; fixed `1.1.0` collided with `error.pack.version_already_installed` |
| Playwright `reuseExistingServer: !CI` | Local Vite on `:5175` no longer fails webServer bootstrap |

## Migrations

- **No new migrations** in Phase 25.
- Pack schema remains Flyway **V9** (core), **V10** (relations), **V11** (import staging).
- Applied migrations were **not** modified.

## Security posture (summary)

- ZIP Slip / bomb / duplicate / unsupported types: `PackArchiveInspector`
- ZIP magic at upload: `PackImportService.looksLikeZip`
- RBAC: ADMIN for pack admin APIs; tenant scoping by `company_id`
- Soft uninstall; historical documents preserved
- No real secrets or PII introduced; demo creds remain DEV placeholders (`changeme_admin_dev_only`)

See also: [security.md](./security.md).

## Known limitations

1. Soft uninstall leaves version history; re-import of an **already-persisted** version while the pack is active returns `error.pack.version_already_installed` (by design for MVP).
2. E2E relies on a live stack (backend `:18081` + Vite `:5175`); Docker frontend on `:5174` may lag code.
3. No gitleaks/trufflehog in CI — only a light local pattern scan for this report.
4. Frontend production chunk > 500 kB (Vite warning); not a functional blocker.
5. LibreOffice / JODConverter may log connection errors in tests when Office is absent; PDF tests still pass where applicable.
6. Spotless / Checkstyle not wired in `pom.xml` (deferred in CI comments).

## Technical debt (non-blocking)

- Hard-delete / DEV pack wipe API for lab DBs (optional).
- Wire Spotless/Checkstyle when the wider codebase is ready.
- Optional Zustand for complex wizards (not required for current import/update UX).
- Frontend unit tests for pack list/wizard beyond Playwright.

## Phase completion docs

| Doc | Role |
|-----|------|
| [PHASE_25.md](./PHASE_25.md) | This phase notes |
| [PHASE_1.md](./PHASE_1.md) … [PHASE_24.md](./PHASE_24.md) | Prior phases |
| [IMPLEMENTATION_ANALYSIS.md](./IMPLEMENTATION_ANALYSIS.md) | Phase 0 audit + status |

## Sign-off checklist

- [x] Backend `mvn verify`
- [x] Frontend lint + build
- [x] Security-related pack tests
- [x] E2E pack principal + update
- [x] Docker compose validation
- [x] Secret scan (light)
- [x] Migrations unchanged / V11 confirmed
- [x] Release report published

**STOP** — no further Business Pack phase without explicit authorization.
