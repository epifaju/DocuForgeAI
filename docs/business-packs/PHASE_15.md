# Phase 15 — Update Engine (completion notes)

**Completed:** 2026-09-06  
**Scope:** Version detection, change analysis / diff, breaking-change detection, update install path, SUPERSEDED versions, historical document preservation (PRD §§90–93)

## Delivered

| Artifact | Path |
|----------|------|
| Change analyzer | `backend/.../businesspack/update/PackChangeAnalyzer.java` |
| Snapshots / DTOs | `PackVersionSnapshot`, `PackChangeAnalysis`, `PackChangeItem`, `PackUpdatePreviewResponse` |
| Update service | `PackUpdateService` (preview only; install reuses Phase 10) |
| API | `GET /api/v1/admin/business-packs/imports/{jobId}/update-preview` |
| Status | `UPDATE_AVAILABLE` set on VALID validate when candidate > installed |
| Audit | `PACK_UPDATED` on UPDATE install (+ `previousVersion` metadata) |
| Tests | `PackChangeAnalyzerTest`, `PackUpdateApiTest` |

## Behaviour

1. **Version detection** — same `pack_key` + SemVer upgrade via `PackCompatibilityService.isUpgrade`
2. **Diff** — templates added/updated/removed; variables added/removed/required; prompts changed
3. **Breaking** — template removed, variable removed, type changed, optional→required, required added; `PACK_SEMVER_BREAKING_CHANGE` when breaking on MINOR/PATCH
4. **Update** — existing `POST .../install` with `PackInstallationType.UPDATE`; previous pack version → `SUPERSEDED`
5. **Historique** — documents keep prior `template_version_id` / storage keys

## Explicit non-goals

- Update UI (Phase 16)
- Uninstall / export / duplicate template
- Blocking install on breaking changes (warnings only)

## Migrations

- None (V9–V11 suffice).

## Verification

```bash
cd backend
mvn -q -Dtest=PackChangeAnalyzerTest,PackCompatibilityServiceTest,PackUpdateApiTest,PackInstallationApiTest test
```
