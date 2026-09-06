# Phase 5 — Manifest Parser (completion notes)

**Completed:** 2026-09-06  
**Scope:** JSON → DTO parsing + JSON Schema + semantic validation for DBPF-1 manifests (no ZIP I/O, no checksum crypto, no install)

## Delivered

| Artifact | Path |
|----------|------|
| Parser | `ai.docuforge.businesspack.manifest.PackManifestParser` |
| Validator | `ai.docuforge.businesspack.manifest.PackManifestValidator` |
| DTO | `PackManifest` (+ nested entries) |
| Result / issues | `PackManifestValidationResult`, `PackValidationIssue`, `PackValidationSeverity` |
| Exception | `PackManifestException` (+ `GlobalExceptionHandler`) |
| Schema loader | `ai.docuforge.businesspack.schema.DbpfSchemaSupport` |
| Tests | `PackManifestParserTest`, `PackManifestValidatorTest` |

## Validation order

1. Parse JSON object (`PACK_MANIFEST_MISSING` / `PACK_MANIFEST_INVALID_JSON`)
2. Early `schemaVersion` check → `PACK_SCHEMA_UNSUPPORTED`
3. JSON Schema (classpath `docuforge-business-pack-v1.schema.json`)
4. Unknown top-level fields → **WARNING** `PACK_MANIFEST_UNKNOWN_FIELD`
5. Semantic checks (locale membership, duplicate template/prompt codes, sample→template ref, undeclared checksum keys as **WARNING**)

## Error / warning codes (Phase 5)

| Code | Severity | Typical HTTP (if thrown) |
|------|----------|---------------------------|
| `PACK_MANIFEST_MISSING` | error (exception) | 400 |
| `PACK_MANIFEST_INVALID_JSON` | error (exception) | 400 |
| `PACK_MANIFEST_SCHEMA_INVALID` | error | 422 (when mapped later) |
| `PACK_SCHEMA_UNSUPPORTED` | error | — |
| `PACK_ID_INVALID` | error | — |
| `PACK_VERSION_INVALID` | error | — |
| `PACK_LOCALE_INVALID` | error | — |
| `PACK_TEMPLATE_CODE_DUPLICATED` | error | — |
| `PACK_PROMPT_DUPLICATED` | error | — |
| `PACK_MANIFEST_UNKNOWN_FIELD` | warning | — |
| `PACK_MANIFEST_CHECKSUM_UNDECLARED` | warning | — |

Unknown fields strategy (PRD §64): **warning**, not hard failure; Jackson ignores unknowns at bind (`@JsonIgnoreProperties`).

## Explicit non-goals

- Cryptographic checksum verification (Phase 6)
- SemVer platform compatibility (Phase 6)
- ZIP file existence / extraction (Phase 8)
- Template DOCX / placeholders (Phase 7)
- REST import API / install

## Migrations

None (reuse Phase 1 schemas + Phase 2–3 tables).

## Verification

```bash
cd backend
mvn -Dtest=PackManifestParserTest,PackManifestValidatorTest,DbpfJsonSchemaTest test
```
