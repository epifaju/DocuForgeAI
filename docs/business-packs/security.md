# Business Pack security

**Audience:** admins, creators, reviewers  
**Runtime:** `PackArchiveInspector`, upload magic check, `@PreAuthorize("hasRole('ADMIN')")`, company scoping.

---

## Threat model (MVP)

| Threat | Defence | Typical code |
|--------|---------|--------------|
| ZIP Slip (`../`, absolute, Windows paths) | Path normalize + reject | `PACK_UNSAFE_PATH` |
| Symlink entries | Reject | `PACK_UNSAFE_PATH` |
| Null-byte / alias duplicate paths (`./a` vs `a`) | Canonical entry keys | `PACK_UNSAFE_PATH` / `PACK_ARCHIVE_INVALID` |
| ZIP bomb (size, entries, compression ratio) | Configurable caps | `PACK_ARCHIVE_LIMIT_EXCEEDED` / `PACK_FILE_TOO_LARGE` |
| Malformed / empty ZIP | Fail open/inspect | `PACK_ARCHIVE_INVALID` |
| MIME spoof (`.zip` without PK header) | Reject at upload | `REQUEST_ERROR` + `error.pack.import_zip_required` |
| Filename path traversal on upload | `StoragePathGuard` | `PATH_TRAVERSAL` |
| Nested ZIP / `.exe` / `.docm` | Extension whitelist | `PACK_UNSUPPORTED_FILE_TYPE` |
| Malformed DOCX | OOXML validation | `PACK_TEMPLATE_INVALID` |
| Cross-tenant access | `company_id` on jobs & packs | `NOT_FOUND` |
| Non-admin access | Method security | `FORBIDDEN` |
| Code execution in pack | Data-only prompts; no script extensions | — |

PKI signatures / marketplace trust are **out of scope** (DBPF-2 / later).

---

## Allowed content

Whitelist (default): `json`, `docx`, `txt`, `md`, `csv`, `png`, `jpg`, `jpeg`, `webp`.

Prompts are plain text consumed by the existing `AIProvider` — packs must not embed runnable code.

Antivirus: when ClamAV (or the configured scanner) is enabled, the archive is scanned before content validation.

---

## Storage

Reuse `StorageProvider` categories (import staging, pack files). Keys remain UUID-style under guarded roots — never trust client paths.

---

## What not to put in packs or logs

- Real customer names, emails, addresses, IBANs
- API keys, passwords, private certificates
- Production secrets of any kind

Use fictional demo data (see artisan demo pack).

---

## Tests

- Unit: `PackArchiveInspectorTest`, `PackImportServiceZipMagicTest`
- API: `PackSecurityHardeningApiTest` (+ RBAC/tenant coverage in other pack API tests)
- See [PHASE_22.md](./PHASE_22.md)

---

## Related

- [DBPF-1.md](./DBPF-1.md) §7 format-level rules  
- [troubleshooting.md](./troubleshooting.md)  
- [importing-a-pack.md](./importing-a-pack.md)
