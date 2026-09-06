# Troubleshooting Business Packs

Quick fixes for common import, validation, and lifecycle failures. Error messages are localized via `error.pack.*`; API responses use the standard `ErrorResponse` envelope (`code`, `message`, `traceId`).

---

## Upload / staging

| Symptom | Likely cause | What to do |
|---------|--------------|------------|
| 400 — only `.zip` accepted | Wrong extension or non-ZIP bytes | Use a real ZIP (local header `PK\x03\x04`) |
| 400 — `PATH_TRAVERSAL` | `../` in original filename | Rename file to a simple `pack-1.0.0.zip` |
| 413 — file too large | Over `PACK_MAX_UPLOAD_SIZE_MB` or servlet multipart | Raise pack + multipart limits together |
| Feature disabled | `docuforge.packs.enabled=false` | Enable packs in config |
| Job not found / 404 | Wrong company or expired job | Re-upload; jobs expire (~24h) |

---

## Validation (`INVALID`)

| Code / message key | Meaning | Fix |
|--------------------|---------|-----|
| `PACK_UNSAFE_PATH` | Zip slip, absolute path, symlink, null byte | Rebuild archive without `..` / links |
| `PACK_ARCHIVE_LIMIT_EXCEEDED` | Too many entries, huge entry, bomb ratio | Shrink content / split pack |
| `PACK_UNSUPPORTED_FILE_TYPE` | Nested zip, `.docm`, exe, … | Remove forbidden files |
| `PACK_MANIFEST_*` | Missing/invalid JSON/schema | Fix `manifest.json` vs JSON Schema |
| `PACK_CHECKSUM_MISMATCH` | File changed after checksum | Recalculate `sha256:` entries |
| `PACK_FILE_MISSING` | Manifest path not in ZIP | Align paths (forward slashes) |
| `PACK_TEMPLATE_UNDECLARED_VARIABLE` | `{{x}}` in DOCX not in metadata | Add variable or remove placeholder |
| `PACK_TEMPLATE_INVALID` | Corrupt DOCX / bad metadata | Re-save DOCX; validate metadata schema |
| `PACK_INCOMPATIBLE…` | Platform SemVer outside range | Adjust compatibility or upgrade DocuForge |

Warnings (e.g. unused variable, unknown manifest field, SemVer breaking on MINOR) do not always block install — read the report.

---

## Install / update / lifecycle

| Symptom | Cause | Fix |
|---------|-------|-----|
| Install requires VALID | Job not validated or INVALID | Fix pack, re-validate |
| Version already installed | Same SemVer active | Bump version or uninstall first |
| Downgrade not allowed | Candidate &lt; installed | Export/install higher SemVer only |
| Template code collision | USER/SYSTEM template owns the code | Rename pack template code or archive collision |
| Cannot enable template | Pack disabled / uninstalled | Enable pack first; or reinstall |
| Update preview empty | Not an upgrade candidate | Ensure same `id` and higher `version` |

---

## Generation after install

| Symptom | Cause | Fix |
|---------|-------|-----|
| Template missing in list | Pack/templates disabled or pagination | Enable; filter/search `ARTISAN_*` |
| Form validation errors | Required fields empty / bad DATE format | Use `YYYY-MM-DD` for dates |
| PDF fails | LibreOffice / JODConverter down | Check backend logs + LibreOffice container |

---

## Isolation & access

| Symptom | Expected |
|---------|----------|
| VIEWER → 403 on pack admin APIs | ADMIN only |
| Other company ADMIN → 404 | Tenant isolation (not 403) |

---

## Diagnostics checklist

1. Confirm backend Flyway ≥ **V11** and `docuforge.packs.enabled=true`.
2. Confirm platform version: `PACK_PLATFORM_VERSION`.
3. Re-download validation report from the import job.
4. Inspect audit log for `PACK_*` actions (no secrets in metadata).
5. Reproduce with artisan demo ZIP from [packs/artisan-demo/](./packs/artisan-demo/).

---

## Related

- [security.md](./security.md)  
- [versioning.md](./versioning.md)  
- [importing-a-pack.md](./importing-a-pack.md)  
- [creating-a-pack.md](./creating-a-pack.md)
