# DBPF-1 Template Metadata Reference

**Files:** `metadata/*.json` (UTF-8)  
**Schema version:** `DBPF-TEMPLATE-1`  
**JSON Schema:** `backend/src/main/resources/schemas/docuforge-template-metadata-v1.schema.json`

Each pack template declares a metadata file that drives form generation after install (mapped into `template_variables`).

---

## Required fields

| Field | Type | Notes |
|-------|------|--------|
| `schemaVersion` | string | `"DBPF-TEMPLATE-1"` |
| `code` | string | Must match manifest template `code` (semantic check) |
| `name` | string | Display name |
| `version` | string | SemVer |
| `outputFormats` | array | Subset of `DOCX`, `PDF` (min 1) |
| `variables` | array | Min 1 variable |

## Optional fields

| Field | Type |
|-------|------|
| `description` | string |
| `category` | string |

---

## Variable object

| Field | Required | Notes |
|-------|----------|--------|
| `key` | yes | Dotted key; see regex below |
| `label` | yes | UI label |
| `type` | yes | See types |
| `required` | yes | boolean |
| `order` | no | Display order (integer ≥ 0) |
| `defaultValue` | no | string/number/boolean/null |
| `placeholder` | no | Hint text |
| `options` | conditional | Required when type is `SELECT` or `MULTISELECT` |
| `validation` | no | Constraints object |
| `ai` | no | Assist hints |

### Variable key regex (DBPF-1)

```regex
^[a-z][a-zA-Z0-9]*(\.[a-zA-Z][a-zA-Z0-9]*)+$
```

Valid: `client.name`, `client.address.city`, `quote.reference`  
Invalid: `client`, `Client.name`, `system.generationDate` (reserved)

### Reserved namespace

Keys under `system.*` are **forbidden** in pack metadata. They are reserved for engine-injected values (e.g. `system.generationDate`, `system.documentReference`) in later phases.

---

## Variable types

| DBPF-1 type | Maps to DocuForge `VariableType` |
|-------------|-----------------------------------|
| `TEXT` | `TEXT` |
| `LONG_TEXT` | `LONG_TEXT` |
| `INTEGER` | **`NUMBER`** |
| `DECIMAL` | `DECIMAL` |
| `CURRENCY` | `CURRENCY` |
| `DATE` | `DATE` |
| `DATETIME` | `DATETIME` |
| `BOOLEAN` | `BOOLEAN` |
| `EMAIL` | `EMAIL` |
| `PHONE` | `PHONE` |
| `SELECT` | `SELECT` |
| `MULTISELECT` | `MULTISELECT` |

Future types (`IMAGE`, `SIGNATURE`, `TABLE`, `REPEATING_SECTION`, `FILE`) are **not** part of DBPF-1 schemas.

---

## SELECT / MULTISELECT options

```json
{
  "key": "contract.type",
  "label": "Type de contrat",
  "type": "SELECT",
  "required": true,
  "options": [
    { "value": "CDI", "label": "CDI" },
    { "value": "CDD", "label": "CDD" }
  ]
}
```

---

## Validation object

```json
{
  "validation": {
    "minLength": 2,
    "maxLength": 150,
    "pattern": null,
    "minimum": null,
    "maximum": null
  }
}
```

Backend remains authoritative after install (`FormDataValidator`). Frontend may mirror rules for UX.

---

## AI assist hints

```json
{
  "ai": {
    "enabled": true,
    "operations": ["FORMALIZE", "REWRITE"],
    "promptCode": "ARTISAN_WORK_DESCRIPTION"
  }
}
```

Notes:

- `promptCode` should reference a prompt declared in the pack manifest.
- Operation strings are free-form at schema level. Current DocuForge `AiOperation` values are `REWRITE`, `FORMALIZE`, `SUMMARIZE`, `GENERATE_PARAGRAPH`. Mapping / unsupported ops are runtime concerns (later phases).
- Prompts are data only; they never execute as code.

---

## Relation to DOCX placeholders

Metadata `key` `client.name` corresponds to DOCX placeholder `{{client.name}}`.

See coherence rules in [DBPF-1.md](./DBPF-1.md) §10.

---

## Example

See `fixtures/valid-template-metadata.json` (fictional artisan devis fields only).
