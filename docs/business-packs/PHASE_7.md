# Phase 7 — DOCX Template Validation (completion notes)

**Completed:** 2026-09-06  
**Scope:** DOCX OOXML checks, placeholder extraction (reuse), metadata schema/semantics, placeholder↔metadata coherence (no install, no full pipeline)

## Delivered

| Artifact | Path |
|----------|------|
| Validator | `ai.docuforge.businesspack.template.PackTemplateValidator` |
| Metadata parser | `PackTemplateMetadataParser` |
| Metadata DTO | `PackTemplateMetadata` |
| Reused | `DocxVariableParser`, `DbpfSchemaSupport` (template metadata schema) |
| Tests | `PackTemplateValidatorTest` (minimal real DOCX via POI) |

## Checks

1. DOCX looks like ZIP OOXML with `[Content_Types].xml` + `word/document.xml` (§138)
2. External `TargetMode.EXTERNAL` relationships → **WARNING** (§140)
3. Metadata JSON Schema (`DBPF-TEMPLATE-1`) + code match + duplicate keys + reserved `system.*`
4. Placeholder extraction via existing `DocxVariableParser` (§33)
5. DOCX key missing from metadata (except `system.*`) → **ERROR** `PACK_TEMPLATE_UNDECLARED_VARIABLE`
6. Metadata key unused in DOCX → **WARNING** `PACK_TEMPLATE_UNUSED_VARIABLE` (§34)
7. `INTEGER` → DocuForge `VariableType.NUMBER` mapping helper

## Explicit non-goals

- Full pack validation pipeline / report assembly (Phase 8)
- ZIP extraction / file existence (Phase 8)
- Install / StorageProvider writes
- Apache Tika MIME detection (later pipeline)
- Macros / `.docm` (already rejected in Phase 4 archive inspector)

## Migrations

None.

## Verification

```bash
cd backend
mvn "-Dtest=PackTemplateValidatorTest,DocxVariableParserTest,DbpfJsonSchemaTest" test
```
