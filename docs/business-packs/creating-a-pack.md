# Creating a DocuForge Business Pack

**Audience:** pack creators  
**Format:** [DBPF-1](./DBPF-1.md)  
**Goal:** ship a valid `.zip` that an ADMIN can import and install.

---

## Create your first pack in 15 minutes

Use the official demo as a working example:

| Item | Value |
|------|--------|
| Pack id | `com.docuforge.pack.artisan-demo` |
| Dist ZIP | [packs/artisan-demo/dist/docuforge-pack-artisan-demo-1.0.0.zip](./packs/artisan-demo/dist/docuforge-pack-artisan-demo-1.0.0.zip) |
| Notes | [packs/artisan-demo/README.md](./packs/artisan-demo/README.md) |

### Steps

1. **Copy the layout** from [DBPF-1 §3](./DBPF-1.md) (manifest + `templates/` + `metadata/`).
2. **Author DOCX** templates with placeholders `{{client.name}}`, `{{quote.reference}}`, … — fictional data only.
3. **Write metadata JSON** (`DBPF-TEMPLATE-1`) for each template — see [template-metadata-reference.md](./template-metadata-reference.md).
4. **Write `manifest.json`** (`DBPF-1`) — see [manifest-reference.md](./manifest-reference.md).
5. **Compute SHA-256 checksums** for every functional file and set `checksums` as `sha256:<64 hex>`.
6. **Zip at the pack root** (or one top-level folder). Filename: `docuforge-pack-{slug}-{version}.zip`.
7. **Import** in DocuForge as ADMIN (UI `/business-packs/import` or API) — see [importing-a-pack.md](./importing-a-pack.md).

Optional: prompts (`.txt`), samples (JSON/CSV), previews (PNG/JPEG/WEBP).

---

## Checklist before you ship

- [ ] `schemaVersion` is `"DBPF-1"`
- [ ] Stable `id` (never changes across versions)
- [ ] SemVer `version` (`MAJOR.MINOR.PATCH`)
- [ ] `compatibility.minimumDocuForgeVersion` matches the target platform (default `0.1.0`)
- [ ] Every template has `templateFile` + `metadataFile` and matching checksums
- [ ] DOCX placeholders ⊆ metadata variables (undeclared → install blocked)
- [ ] No `.docm`, nested `.zip`, or executables
- [ ] No real personal data — use fictional samples only
- [ ] No secrets in prompts, samples, or README

---

## Checksums (quick recipe)

```bash
# Example (PowerShell): sha256 of a file, then prefix for the manifest
Get-FileHash templates/demo.docx -Algorithm SHA256
# → checksums entry: "templates/demo.docx": "sha256:<lowercase-hex>"
```

Rebuild the ZIP after any file change and refresh checksums.

---

## Variable rules (summary)

| Rule | Detail |
|------|--------|
| Keys | Dotted, e.g. `client.name` |
| Reserved | `system.*` forbidden in pack metadata |
| Types | TEXT, LONG_TEXT, NUMBER/INTEGER→NUMBER, DATE, CURRENCY, EMAIL, … |
| AI | Optional `ai` hints + `promptCode` — data only, executed via existing `AIProvider` |

---

## Regenerating the artisan demo ZIP

```bash
cd backend
mvn -Dtest=ArtisanDemoPackFactoryTest,ArtisanDemoPackApiTest test
```

Factory: `ai.docuforge.businesspack.demo.ArtisanDemoPackFactory`  
See [PHASE_21.md](./PHASE_21.md).

---

## Related docs

| Doc | Use when |
|-----|----------|
| [DBPF-1.md](./DBPF-1.md) | Full format rules |
| [versioning.md](./versioning.md) | SemVer / upgrades / breaking changes |
| [security.md](./security.md) | What the platform rejects |
| [troubleshooting.md](./troubleshooting.md) | Validation failures |
