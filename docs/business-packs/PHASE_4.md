# Phase 4 — Secure Archive Inspector (completion notes)

**Completed:** 2026-09-06  
**Scope:** Non-extracting ZIP security scan only (no install, no manifest parse)

## Delivered

| Artifact | Path |
|----------|------|
| Inspector | `ai.docuforge.businesspack.archive.PackArchiveInspector` |
| Result DTO | `PackArchiveInspection` |
| Exception | `PackArchiveException` (+ `GlobalExceptionHandler`) |
| Config | `PackProperties` / `docuforge.packs.*` |
| Dependency | `commons-compress:1.27.1` |
| Tests | `PackArchiveInspectorTest` |

## Checks performed (headers only)

- Archive file size vs `max-upload-size-mb`
- ZIP open / validity
- Entry count vs `max-files`
- Per-entry uncompressed size vs `max-single-file-mb`
- Total uncompressed vs `max-uncompressed-size-mb`
- Compression ratio (uncompressed / compressed) vs `max-compression-ratio`
- Template DOCX count under `templates/` vs `max-templates`
- Path safety (zip-slip, absolute, Windows drive, `..`)
- Symlink rejection (`isUnixSymlink`)
- Extension whitelist (`.docm` always rejected)
- Duplicate entry names

## Error codes

| Code | Typical HTTP |
|------|----------------|
| `PACK_ARCHIVE_INVALID` | 400 |
| `PACK_ARCHIVE_LIMIT_EXCEEDED` | 413 |
| `PACK_FILE_TOO_LARGE` | 413 |
| `PACK_UNSAFE_PATH` | 400 |
| `PACK_UNSUPPORTED_FILE_TYPE` | 415 |

## Explicit non-goals

- Extraction / staging promotion
- Manifest JSON Schema (Phase 5)
- ClamAV scan (pipeline later; scanner already exists)
- REST import API

## Verification

```bash
cd backend
mvn -Dtest=PackArchiveInspectorTest,DbpfJsonSchemaTest test
```
