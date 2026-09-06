# DBPF-1 Manifest Reference

**File:** `manifest.json` (UTF-8)  
**Schema version:** `DBPF-1`  
**JSON Schema:** `backend/src/main/resources/schemas/docuforge-business-pack-v1.schema.json`

---

## Required fields

| Field | Type | Notes |
|-------|------|--------|
| `schemaVersion` | string | Must be `"DBPF-1"` |
| `id` | string | Stable pack key; regex `^[a-z][a-z0-9]*(\.[a-z][a-z0-9-]*){2,}$` |
| `name` | string | Display name |
| `slug` | string | Regex `^[a-z0-9]+(?:-[a-z0-9]+)*$` |
| `version` | string | SemVer `MAJOR.MINOR.PATCH` |
| `type` | enum | `OFFICIAL` \| `CUSTOM` \| `THIRD_PARTY` |
| `description` | string | Non-empty |
| `publisher` | object | Requires `id`, `name` |
| `compatibility` | object | Requires `minimumDocuForgeVersion` |
| `locales` | string[] | BCP 47 tags; min 1 |
| `defaultLocale` | string | BCP 47; **must be ∈ locales** (semantic rule) |
| `templates` | array | Min 1 template entry |
| `checksums` | object | Path → `sha256:<64 hex>` |

---

## Optional fields

| Field | Type | Notes |
|-------|------|--------|
| `categories` | string[] | Free-form category labels |
| `tags` | string[] | Search tags |
| `prompts` | array | Prompt declarations |
| `samples` | array | JSON/CSV sample declarations |
| `compatibility.maximumDocuForgeVersion` | string \| null | SemVer or `N.x` |

Unknown properties are allowed by schema (`additionalProperties: true`) and should produce **warnings** in semantic validation (Phase 5), not hard schema failures.

---

## `id` examples

Valid:

```text
com.docuforge.pack.artisan
com.acme.docuforge.pack.custom-sales
```

Invalid:

```text
Artisan
com.docuforge
pack_artisan
```

The `id` never changes between versions of the same pack.

---

## `slug`

Used in archive naming (`docuforge-pack-{slug}-{version}.zip`) and UI.

Valid: `artisan`, `cabinet-conseil`  
Invalid: `Cabinet`, `cabinet_conseil`, `-artisan`

---

## `type`

| Value | Meaning |
|-------|---------|
| `OFFICIAL` | Distributed by DocuForge |
| `CUSTOM` | Created by a customer organization (`company_id` scoped at install) |
| `THIRD_PARTY` | Reserved for future marketplace |

---

## `publisher`

```json
{
  "id": "docuforge",
  "name": "DocuForge AI"
}
```

---

## `compatibility`

```json
{
  "minimumDocuForgeVersion": "0.1.0",
  "maximumDocuForgeVersion": null
}
```

Comparison uses SemVer semantics (`PackCompatibilityService` + semver4j). Do not compare versions lexicographically.

---

## `templates[]` entry

| Field | Required | Notes |
|-------|----------|--------|
| `code` | yes | Upper snake; `^[A-Z][A-Z0-9_]{2,149}$` |
| `name` | yes | Display name |
| `version` | yes | Template SemVer inside the pack |
| `templateFile` | yes | Relative path, typically `templates/*.docx` |
| `metadataFile` | yes | Relative path, typically `metadata/*.json` |
| `previewFile` | no | `previews/*.(png\|jpg\|jpeg\|webp)` |
| `enabledByDefault` | no | Default `true` |

Paths MUST be relative, must not contain `..`, and must not be absolute.

---

## `prompts[]` entry

| Field | Required |
|-------|----------|
| `code` | yes |
| `version` | yes |
| `file` | yes (`.txt`) |

Prompt content is opaque text for `AIProvider`. It must never grant DB access, shell, or privilege escalation.

---

## `samples[]` entry

| Field | Required | Notes |
|-------|----------|--------|
| `code` | yes | |
| `type` | yes | `JSON` or `CSV` |
| `file` | yes | |
| `templateCode` | no | Links sample to a template code |

Samples are illustrative only. Official packs must not include real personal data.

---

## `checksums`

```json
{
  "templates/artisan-devis.docx": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "metadata/artisan-devis.json": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

Every functional file referenced by the manifest SHOULD appear here. Missing declared checksum or mismatch blocks installation (`PACK_CHECKSUM_MISMATCH`).

---

## Minimal valid skeleton

See `fixtures/valid-manifest.json` and classpath `packs/dbpf/valid-manifest.json`.

---

## Error codes (schema / early validation)

| Code | Typical cause |
|------|----------------|
| `PACK_MANIFEST_MISSING` | No manifest.json |
| `PACK_MANIFEST_INVALID_JSON` | Parse failure |
| `PACK_MANIFEST_SCHEMA_INVALID` | JSON Schema failure |
| `PACK_SCHEMA_UNSUPPORTED` | Wrong `schemaVersion` |
| `PACK_ID_INVALID` | Bad `id` |
| `PACK_VERSION_INVALID` | Bad SemVer |
| `PACK_LOCALE_INVALID` | Bad locale / defaultLocale ∉ locales |

API errors use the existing DocuForge `ErrorResponse` envelope when exposed over HTTP (later phases).
