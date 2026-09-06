# DocuForge Business Pack Format — DBPF-1

**Status:** Official specification (maintained)  
**Schema version:** `DBPF-1`  
**Template metadata schema:** `DBPF-TEMPLATE-1`  
**Source PRD:** `docs/PRD_BUSINESS_PACK_SYSTEM.md`  
**Machine schemas:**

- `backend/src/main/resources/schemas/docuforge-business-pack-v1.schema.json`
- `backend/src/main/resources/schemas/docuforge-template-metadata-v1.schema.json`

**Companion guides:** [creating-a-pack.md](./creating-a-pack.md) · [importing-a-pack.md](./importing-a-pack.md) · [security.md](./security.md) · [versioning.md](./versioning.md) · [troubleshooting.md](./troubleshooting.md)

This document is the **official format specification** for distributable DocuForge business packs. Runtime admin flows are documented in [importing-a-pack.md](./importing-a-pack.md).

---

## 1. Purpose

A DBPF-1 pack is a versioned ZIP that can distribute:

- DOCX templates
- variable / form metadata
- optional AI prompts
- optional sample data
- optional preview images
- checksums and compatibility metadata

The pack is a **distribution layer**. Document generation remains the existing generic Template Engine (`StorageProvider`, `DocumentGenerator`, `PdfConverter`, `AIProvider`). Packs must never introduce generator branches such as `if (pack.equals("ARTISAN"))`.

---

## 2. Package naming

| Item | Rule |
|------|------|
| Archive extension | `.zip` only |
| Recommended filename | `docuforge-pack-{slug}-{version}.zip` |
| Example | `docuforge-pack-artisan-1.0.0.zip` |

---

## 3. Archive root layout

Logical tree (example Pack Artisan demo — fictional content only):

```text
docuforge-pack-artisan-1.0.0/
├── manifest.json          # required
├── README.md              # recommended
├── CHANGELOG.md           # recommended
├── LICENSE.txt            # recommended
├── templates/             # required when templates[] non-empty
│   └── artisan-devis.docx
├── metadata/              # one JSON per template
│   └── artisan-devis.json
├── prompts/               # optional
│   └── work-description.txt
├── samples/               # optional (JSON or CSV)
│   └── artisan-devis.json
├── previews/              # optional (PNG/JPEG/WEBP)
│   └── artisan-devis.png
└── assets/                # optional non-executable assets
    └── README.md
```

### Root normalization (§16)

Accept either:

1. ZIP entries starting at pack root (`manifest.json`, `templates/...`), or
2. a single top-level directory containing that tree.

Parsers MUST normalize to a virtual root before validation. Multiple top-level directories without a shared root are invalid.

---

## 4. Required vs optional files

| Path | Required |
|------|----------|
| `manifest.json` | **Yes** (UTF-8) |
| Each `templates/*.docx` declared in manifest | **Yes** |
| Each `metadata/*.json` declared in manifest | **Yes** |
| Declared prompt / sample / preview files | **Yes** if referenced |
| `README.md`, `CHANGELOG.md`, `LICENSE.txt` | Recommended |
| `assets/**` | Optional |

---

## 5. Allowed file types (MVP whitelist)

```text
.json  .docx  .txt  .md  .csv  .png  .jpg  .jpeg  .webp
```

Refuse executable / script types (non-exhaustive): `.exe`, `.dll`, `.bat`, `.cmd`, `.ps1`, `.sh`, `.jar`, `.class`, `.js`, `.py`, `.php`, `.com`, `.scr`, `.msi`, `.docm`.

A pack MUST NOT be able to execute code inside DocuForge.

---

## 6. Configurable limits (runtime)

Documented for format authors; enforced at import via `docuforge.packs.*`:

| Setting | Example default |
|---------|-----------------|
| Max upload size | 100 MB |
| Max uncompressed size | 250 MB |
| Max entries | 500 |
| Max templates | 100 |
| Max single file | 25 MB |
| Max compression ratio | 50 |
| Import retention | 24 h |

See also [security.md](./security.md) and [importing-a-pack.md](./importing-a-pack.md).

---

## 7. Security constraints (format-level)

Enforced by archive inspector (Phase 4+), specified here for authors:

| Threat | Rule | Error code |
|--------|------|------------|
| Zip bomb | Bound compressed/uncompressed size, entry count, ratio | `PACK_ARCHIVE_LIMIT_EXCEEDED` |
| Zip slip | Reject `..`, absolute paths, Windows drive paths | `PACK_UNSAFE_PATH` |
| Symlinks | Reject | `PACK_UNSAFE_PATH` / archive invalid |
| Malware | Full-archive antivirus scan when enabled | implementation-specific |
| Content trust | Do not trust extension/MIME alone; validate OOXML for DOCX; ZIP magic at upload | `PACK_TEMPLATE_INVALID` / import zip required |

Signature / PKI is **out of scope** for DBPF-1 (reserved for DBPF-2). Details: [security.md](./security.md).

---

## 8. Manifest

- File: `manifest.json`
- Encoding: UTF-8
- `schemaVersion`: must be `"DBPF-1"`
- Validated against `docuforge-business-pack-v1.schema.json` before semantic checks

See [manifest-reference.md](./manifest-reference.md).

Unknown JSON properties: **warning** during semantic validation (not a schema hard-fail); schemas use `additionalProperties: true` at object roots to allow forward-compatible fields.

---

## 9. Template metadata

Each template entry references a metadata JSON (`schemaVersion: DBPF-TEMPLATE-1`) describing variables, types, validations, and optional AI assist hints.

See [template-metadata-reference.md](./template-metadata-reference.md).

Validated against `docuforge-template-metadata-v1.schema.json`.

---

## 10. Placeholders (DOCX)

Official syntax (aligned with existing DocuForge engine):

```text
{{company.name}}
{{client.name}}
{{quote.reference}}
```

Whitespace inside braces is allowed by the engine; metadata keys MUST use the dotted form without braces.

Coherence rules (runtime Phase 7):

| Case | Severity | Code |
|------|----------|------|
| Placeholder in DOCX without metadata variable | ERROR | `PACK_TEMPLATE_UNDECLARED_VARIABLE` |
| Metadata variable unused in DOCX | WARNING | `PACK_TEMPLATE_UNUSED_VARIABLE` |

---

## 11. Checksums

- Algorithm: SHA-256
- Manifest format: `sha256:` + 64 hex chars
- Cover at least all functional files: templates, metadata, prompts (previews/samples recommended)

Mismatch → `PACK_CHECKSUM_MISMATCH` (blocks install).

**Adaptation note:** installed `template_versions.checksum` today stores raw hex without prefix. Import/export layers MUST normalize (`sha256:` in DBPF ↔ hex in DB).

---

## 12. Versioning

- Pack `version` and template/prompt versions: SemVer `MAJOR.MINOR.PATCH`
- Pack `id` is immutable across versions
- Installed pack versions are immutable; publish a new SemVer instead of mutating

Compatibility fields:

- `compatibility.minimumDocuForgeVersion` (required)
- `compatibility.maximumDocuForgeVersion` (nullable SemVer or major range like `1.x`)

**Adaptation note:** DocuForge platform SemVer for packs is `docuforge.packs.platform-version` (default `0.1.0`). See [versioning.md](./versioning.md).

---

## 13. Mapping to existing DocuForge (authoritative adaptations)

| DBPF-1 / PRD term | Existing implementation |
|-------------------|-------------------------|
| Organization | `Company` / `company_id` |
| Java package (PRD `com.docuforge.pack`) | `ai.docuforge.businesspack` |
| Variable type `INTEGER` | `VariableType.NUMBER` on install |
| Variable keys (dotted) | Stricter in DBPF-1 than current upload parser; packs MUST use dotted keys |
| Reserved `system.*` | Forbidden in pack metadata variables |
| Storage | Reuse `StorageProvider` (staging + pack files) |
| AI prompts | Data only → `AIProvider` (no code execution) |
| Errors | `ErrorResponse` envelope (`code`, `message`, `traceId`, …) |
| Flyway | Pack migrations **V9–V11** (never edit applied migrations) |

---

## 14. Examples / fixtures

Human-readable copies:

- `docs/business-packs/fixtures/`

Classpath test fixtures:

- `backend/src/test/resources/packs/dbpf/`

All sample data is **fictional** (no real personal data).

---

## 15. Out of scope for DBPF-1 MVP

Marketplace, payments, DRM, mandatory PKI signatures, remote auto-update, ratings, creator payouts.

---

## 16. Maintenance

Format changes that break existing packs require a new schema version (e.g. DBPF-2). Additive optional fields may ship as warnings under `additionalProperties`.

Operational docs: [creating-a-pack.md](./creating-a-pack.md), [importing-a-pack.md](./importing-a-pack.md), [troubleshooting.md](./troubleshooting.md).
