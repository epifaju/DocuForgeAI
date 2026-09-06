# Phase 2 — Domain Model (completion notes)

**Completed:** 2026-09-06  
**Scope:** Core pack domain tables + JPA model only (no relations Phase 3, no API)

## Delivered

| Artifact | Path |
|----------|------|
| Migration | `backend/src/main/resources/db/migration/V9__business_pack_core.sql` |
| Entities | `ai.docuforge.domain.businesspack.BusinessPack`, `BusinessPackVersion` |
| Enums | `BusinessPackType`, `BusinessPackStatus`, `PackVersionStatus` |
| Repositories | `BusinessPackRepository`, `BusinessPackVersionRepository` |
| Tests | `BusinessPackDomainTest`, extended `SchemaMigrationTest` |

## Adaptations vs PRD SQL

| PRD | Implementation |
|-----|----------------|
| `organization_id` | `company_id` → `companies(id)`, nullable |
| `UNIQUE (organization_id, pack_key)` | Partial unique indexes: per-company + global (`company_id IS NULL`) |
| Package `com.docuforge.pack` | `ai.docuforge.domain.businesspack` |

## Explicit non-goals

- `business_pack_templates` / prompts / files / installations / import jobs (Phase 3)
- Template `origin` columns (Phase 3)
- REST APIs, ZIP, install engine

## Verification

```bash
cd backend
mvn -Dtest=BusinessPackDomainTest,SchemaMigrationTest,DbpfJsonSchemaTest test
```

Requires Docker for Testcontainers.
