# Admin guide — importing & managing packs

**Audience:** company ADMIN  
**UI:** `/business-packs` (nav: Packs métier)  
**API prefix:** `/api/v1/admin/business-packs`  
**Auth:** JWT + role `ADMIN` (VIEWER → 403). All rows are scoped to `company_id` (cross-tenant → 404).

---

## Import wizard (recommended)

1. Open **Packs métier** → **Importer un pack**.
2. Upload a `.zip` (DBPF-1). The platform checks ZIP magic (`PK…`), size, and filename safety.
3. Wait for validation (auto after upload, or retry).
4. Review the report (errors block install; warnings may continue).
5. If an older version is installed, review the **update diff**, then confirm **Installer** / **Mettre à jour**.
6. Options: enable pack and/or templates after install.

Nothing is installed until you confirm.

### API sequence

```http
POST   /api/v1/admin/business-packs/import          # multipart file → jobId (202)
POST   /api/v1/admin/business-packs/imports/{id}/validate
GET    /api/v1/admin/business-packs/imports/{id}/update-preview   # when upgrade candidate
POST   /api/v1/admin/business-packs/imports/{id}/install
       Body: { "enablePack": true, "enableTemplates": true }
GET    /api/v1/admin/business-packs/imports/{id}
```

Staging jobs expire after `docuforge.packs.import-retention-hours` (default 24h).

---

## Day-2 operations

| Action | UI | API |
|--------|----|-----|
| List / filter packs | Packs métier | `GET /api/v1/admin/business-packs` |
| Detail, versions, templates | Pack detail | `GET .../{packId}`, `.../versions`, `.../templates` |
| Enable / disable pack | Detail / list | `POST .../{packId}/enable` \| `disable` |
| Enable / disable template | Detail → templates | `POST .../{packId}/templates/{code}/enable` \| `disable` |
| Duplicate template → USER | Detail | Template duplicate API (independent USER copy) |
| Uninstall (logical) | Detail → Désinstaller | `DELETE .../{packId}` |
| Export DBPF-1 ZIP | Detail → Export | `GET` or `POST .../{packId}/export` |

### Uninstall behaviour

- Soft status `UNINSTALLED` — **no hard delete** of historical documents or storage blobs required for history.
- Pack templates are no longer offered for **new** generations.
- Same SemVer can be reinstalled later (REINSTALL path).

### Update behaviour

- Same immutable `packKey` / `id`, higher SemVer.
- Previous version → `SUPERSEDED`.
- Breaking changes (removed template/variable, type change, optional→required) are reported; install is not hard-blocked on SemVer mismatch warnings alone.
- Documents keep their prior `template_version_id` / files.

---

## Feature flag & limits

| Setting | Env / property | Default |
|---------|----------------|---------|
| Enable packs | `DOCUFORGE_PACKS_ENABLED` / `docuforge.packs.enabled` | true |
| Platform SemVer for compatibility | `PACK_PLATFORM_VERSION` | `0.1.0` |
| Max upload | `PACK_MAX_UPLOAD_SIZE_MB` | 100 |
| Max uncompressed | `PACK_MAX_UNCOMPRESSED_SIZE_MB` | 250 |
| Max entries / templates / single file / ratio | see `application.yml` `docuforge.packs.*` | 500 / 100 / 25 MB / 50 |

Also ensure Spring multipart limits are ≥ pack upload size.

---

## Audit

Successful and failed pack actions are written to the existing audit log (upload, validate, install, update, enable/disable, uninstall, export). Prefer action names such as `PACK_*` — never log secrets or real PII.

---

## Related docs

- [creating-a-pack.md](./creating-a-pack.md) — for pack authors  
- [security.md](./security.md) — threats & codes  
- [versioning.md](./versioning.md) — SemVer policy  
- [troubleshooting.md](./troubleshooting.md) — common failures  
- [docs/e2e.md](../e2e.md) — Playwright smoke (§§188–189)
