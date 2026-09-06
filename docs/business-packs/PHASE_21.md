# Phase 21 — Artisan Demo Pack (completion notes)

**Completed:** 2026-09-06  
**Scope:** Official demo pack `com.docuforge.pack.artisan-demo` (PRD §§161–164)

## Delivered

| Artifact | Path |
|----------|------|
| Factory | `ArtisanDemoPackFactory` |
| Dist ZIP | `docs/business-packs/packs/artisan-demo/dist/docuforge-pack-artisan-demo-1.0.0.zip` |
| Pack docs | `docs/business-packs/packs/artisan-demo/README.md` |
| Tests | `ArtisanDemoPackFactoryTest`, `ArtisanDemoPackApiTest` |
| Key fix | `VariableKeyRules` — `description` no longer blocked by `script` substring |

## Pack contents

- **3 templates:** `ARTISAN_DEVIS`, `ARTISAN_INTERVENTION`, `ARTISAN_COMPLETION_CERTIFICATE`
- **2 prompts:** `ARTISAN_WORK_DESCRIPTION`, `ARTISAN_INTERVENTION_NOTES`
- **Samples:** JSON devis + CSV intervention
- **Previews:** PNG per template
- **Data:** fictional only; completion certificate marked as private demo (not an official act)

## Behaviour

1. Factory builds a valid DBPF-1 ZIP with recalculated `sha256:` checksums.
2. Offline structure test + full `PackValidationService` + install API test.
3. Generation from `ARTISAN_DEVIS` succeeds with sample fictional data.
4. `VariableKeyRules` rejects path segment `script` / `javascript:` without rejecting `*.description`.

## Explicit non-goals

- Security hardening campaign (Phase 22)
- E2E Playwright flow (Phase 23)
- Pack builder UI

## Migrations

- None.

## Verification

```bash
cd backend
mvn -Dtest=ArtisanDemoPackFactoryTest,ArtisanDemoPackApiTest,VariableKeyRulesTest test
```

## Materialize dist ZIP

```bash
cd backend
mvn -q -DskipTests compile
# then run MaterializeArtisanDemo (see Phase 21 session) writing to
# docs/business-packs/packs/artisan-demo/dist/docuforge-pack-artisan-demo-1.0.0.zip
```
