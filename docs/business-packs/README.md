# DocuForge Business Packs

Documentation for the **DocuForge Business Pack System** (format DBPF-1).

## Guides (start here)

| Document | Audience |
|----------|----------|
| [creating-a-pack.md](./creating-a-pack.md) | Creators — first pack in 15 minutes |
| [importing-a-pack.md](./importing-a-pack.md) | Admins — import, update, lifecycle, export |
| [DBPF-1.md](./DBPF-1.md) | Official format specification |
| [manifest-reference.md](./manifest-reference.md) | `manifest.json` fields |
| [template-metadata-reference.md](./template-metadata-reference.md) | Template metadata fields |
| [security.md](./security.md) | Threats, codes, hard rules |
| [versioning.md](./versioning.md) | SemVer, upgrades, history |
| [troubleshooting.md](./troubleshooting.md) | Common failures |
| [BUSINESS_PACK_RELEASE_REPORT.md](./BUSINESS_PACK_RELEASE_REPORT.md) | Phase 25 — release readiness |

## Reference & demos

| Document | Description |
|----------|-------------|
| [packs/artisan-demo/](./packs/artisan-demo/) | Official demo pack + dist ZIP |
| [fixtures/](./fixtures/) | Sample JSON (fictional data only) |
| [IMPLEMENTATION_ANALYSIS.md](./IMPLEMENTATION_ANALYSIS.md) | Phase 0 — audit vs existing DocuForge |
| [../e2e.md](../e2e.md) | Playwright E2E (Core + packs) |

**Machine-readable schemas** (backend classpath):

```text
backend/src/main/resources/schemas/docuforge-business-pack-v1.schema.json
backend/src/main/resources/schemas/docuforge-template-metadata-v1.schema.json
```

## Phase completion notes

| Doc | Phase |
|-----|-------|
| [PHASE_1.md](./PHASE_1.md) … [PHASE_25.md](./PHASE_25.md) | Implementation + release notes |

**Source of truth (product):** `docs/PRD_BUSINESS_PACK_SYSTEM.md`
