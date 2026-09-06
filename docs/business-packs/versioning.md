# Pack versioning

**Audience:** creators and admins  
**Policy:** SemVer `MAJOR.MINOR.PATCH` on the pack `version` field; pack `id` / `packKey` is **immutable**.

---

## Rules

| Rule | Behaviour |
|------|-----------|
| Identity | `id` never changes between releases of the same product pack |
| Immutability | An installed version is not edited in place — publish a new SemVer |
| Upgrade | Higher SemVer → UPDATE install; previous version → `SUPERSEDED` |
| Downgrade | Refused (`error.pack.downgrade_not_allowed` / conflict) |
| Same version | If already installed and not uninstalled → conflict; if `UNINSTALLED` → REINSTALL |
| Platform compatibility | `compatibility.minimumDocuForgeVersion` (required); optional `maximumDocuForgeVersion` |
| Platform SemVer source | `docuforge.packs.platform-version` / `PACK_PLATFORM_VERSION` (default `0.1.0`) |

Template and prompt entries also carry SemVer strings in the manifest for documentation and diffing.

---

## What counts as breaking (update preview)

Reported by `PackChangeAnalyzer` (install may still proceed; treat as review gate):

- Template removed
- Variable removed
- Variable type changed
- Optional → required
- Required variable added

`PACK_SEMVER_BREAKING_CHANGE` warning when breaking changes appear on a MINOR/PATCH bump (SemVer hygiene).

Non-breaking examples: new template, new optional variable, prompt text change, description-only bump.

---

## Historical documents

- Generated documents keep the **template version** and storage keys used at generation time.
- Updating a pack does **not** rewrite past PDFs/DOCX.
- Uninstall does **not** purge historical documents.

---

## Export / round-trip

Export rebuilds a DBPF-1 ZIP with recalculated checksums for the current version. Round-trip (export → import → validate → install) is covered by backend tests and E2E update flow — see [PHASE_20.md](./PHASE_20.md), [PHASE_23.md](./PHASE_23.md).

---

## Related

- [manifest-reference.md](./manifest-reference.md)  
- [creating-a-pack.md](./creating-a-pack.md)  
- [importing-a-pack.md](./importing-a-pack.md)  
- [PHASE_15.md](./PHASE_15.md)
