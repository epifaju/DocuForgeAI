# Phase 1 — DBPF-1 Specification (completion notes)

**Completed:** 2026-09-06  
**Scope:** Format specification only (no DB, no API, no install)

## Delivered

| Artifact | Path |
|----------|------|
| Format spec | `DBPF-1.md` |
| Manifest reference | `manifest-reference.md` |
| Template metadata reference | `template-metadata-reference.md` |
| Manifest JSON Schema | `backend/src/main/resources/schemas/docuforge-business-pack-v1.schema.json` |
| Template metadata JSON Schema | `backend/src/main/resources/schemas/docuforge-template-metadata-v1.schema.json` |
| Human fixtures | `fixtures/*.json` |
| Test fixtures | `backend/src/test/resources/packs/dbpf/*.json` |
| Schema tests | `backend/src/test/java/ai/docuforge/businesspack/schema/DbpfJsonSchemaTest.java` |
| Validator dependency | `com.networknt:json-schema-validator:1.5.7` (pom.xml) |

## Explicit non-goals (this phase)

- No Flyway migrations
- No entities / repositories
- No REST endpoints
- No ZIP inspector
- No frontend changes
- No StorageProvider / AIProvider behavior changes

## Adaptations recorded for later phases

- `organization_id` → `company_id`
- Java package → `ai.docuforge.businesspack`
- DBPF `INTEGER` → `VariableType.NUMBER`
- Checksum `sha256:` in packs vs raw hex in `template_versions`
- Platform SemVer still `0.1.0` (fixtures use `minimumDocuForgeVersion: "0.1.0"`)

## Verification

```bash
cd backend
mvn -Dtest=DbpfJsonSchemaTest test
```

Integration tests requiring Testcontainers/Docker are unchanged and out of Phase 1 scope.
