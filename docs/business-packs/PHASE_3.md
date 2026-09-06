# Phase 3 — Pack Relations (completion notes)

**Completed:** 2026-09-06  
**Scope:** Relation tables + template provenance columns (no API, no ZIP, no install engine)

## Delivered

| Artifact | Path |
|----------|------|
| Migration | `backend/src/main/resources/db/migration/V10__business_pack_relations.sql` |
| Entities | `BusinessPackTemplate`, `BusinessPackPrompt`, `BusinessPackFile`, `BusinessPackInstallation`, `PackImportJob` |
| Enums | `TemplateOrigin`, `PackFileType`, `PackInstallationType`, `PackInstallationStatus`, `PackImportJobStatus` |
| Template provenance | `Template.origin`, `Template.sourcePack`, `TemplateVersion.sourcePackVersion`, `TemplateVersion.sourceTemplateCode` |
| Repositories | matching `*Repository` interfaces |
| Tests | `BusinessPackRelationsTest`, extended `SchemaMigrationTest` |

## Adaptations vs PRD

| PRD | Implementation |
|-----|----------------|
| `organization_id` on installations/import jobs | `company_id` |
| `installation_type` (unspecified enum) | `FRESH`, `UPDATE`, `REINSTALL` |
| Installation `status` | `ACTIVE`, `DISABLED`, `UNINSTALLED` |
| Existing templates without origin | backfilled `USER` via `DEFAULT 'USER'` |
| Manual template create | sets `TemplateOrigin.USER` |

## Explicit non-goals

- Archive inspector / ZIP security (Phase 4)
- Manifest parser (Phase 5)
- Import/install REST APIs
- `generated_documents.pack_id` (historical lineage — later if required by install phase)

## Verification

```bash
cd backend
mvn -Dtest=BusinessPackRelationsTest,BusinessPackDomainTest,SchemaMigrationTest,DbpfJsonSchemaTest test
```
