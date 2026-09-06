# DocuForge Business Pack System — Phase 0 Implementation Analysis

**Source of truth:** `docs/PRD_BUSINESS_PACK_SYSTEM.md` (PRD v1.1, format DBPF-1)  
**Scope:** Audit of the existing DocuForge AI repository (Phase 0)  
**Dates:** Phase 0 — 2026-09-06; Phase 1 specs — 2026-09-06  
**Status:** Phase 0–9 delivered (through ADMIN pack import API / staging / retention)  

---

## 1. CURRENT ARCHITECTURE

DocuForge AI is a self-hosted monolith:

| Layer | Stack |
|-------|--------|
| Backend | Java **21**, Spring Boot **3.4.4**, Spring Security, JPA/Hibernate, Flyway, springdoc OpenAPI |
| Frontend | React **19**, TypeScript **5.8**, Vite **6**, Tailwind **4**, TanStack Query **5**, React Hook Form + Zod, i18next (FR/PT) |
| Data | PostgreSQL **16** |
| Docs runtime | Apache POI **5.4.0** (DOCX), JODConverter + LibreOffice UNO (PDF) |
| AI | Optional Ollama via `AIProvider` |
| Storage | Local filesystem via `StorageProvider` (MinIO profile exists but unused by app code) |
| Ops | Docker Compose (+ optional ClamAV, Redis, MinIO, Traefik, n8n), GitHub Actions CI |

**Tenancy model:** `Company` (`companies`) — **not** an `Organization` entity. JWT claims include `companyId` / `companyIdentifier`. Almost all domain rows are `company_id`-scoped.

**Base package:** `ai.docuforge` (PRD Business Pack suggests `com.docuforge.pack` — naming must be adapted).

**Architectural fit with PRD §3:** The Business Pack System is intended as a **distribution/configuration layer above** the existing Template Engine. The current codebase already has a reusable template → variables → form → DOCX/PDF pipeline that packs should feed, without pack-specific branches in the generator.

---

## 2. EXISTING REUSABLE COMPONENTS

| Component | Location | Reuse for Business Packs |
|-----------|----------|--------------------------|
| `Template` / `TemplateVersion` / `TemplateVariable` | `domain.template.*` | Install pack templates into these tables |
| `TemplateService` + upload/activate/archive | `template.TemplateService` | Pattern for DOCX store + SHA-256 + version bump; pack install should orchestrate similarly (not call pack-specific generator logic) |
| `DocxVariableParser` + `VariableKeyRules` | `template.parser.*` | Placeholder extraction / DOCX validation (§33–34, Phase 7) |
| `FormSchemaService` / `FormDataValidator` | `form.*` | Forms from installed variables |
| `DocumentGenerator` / `PoiDocxDocumentGenerator` | `document.engine.*` | Generation remains generic |
| `PdfConverter` / `JodConverterPdfConverter` | `document.pdf.*` | Unchanged |
| `StorageProvider` / `LocalStorageProvider` | `storage.*` | Must extend categories/key rules for packs |
| `AntivirusScanner` (+ ClamAV / NoOp) | `security.antivirus.*` | Scan ZIP before extract (§65) |
| `AIProvider` / `OllamaAIProvider` | `ai.*` | Pack prompts become data fed to AI — catalog today is classpath-only |
| `AuditService` / `AuditActions` | `audit.*` | Extend with `PACK_*` actions (§105) |
| `ApiResponse` / `ErrorResponse` / `GlobalExceptionHandler` | `common.api` / `common.web` | Match §134 error envelope |
| Auth JWT + httpOnly cookies + `@PreAuthorize` | `auth.*`, `config.SecurityConfig` | ADMIN for pack admin APIs (§78, §102) |
| Batch `@Async` + job entity shape | `batch.*` | Closest pattern for `pack_import_jobs` (see gaps: no SKIP LOCKED yet) |
| Admin settings / users UI pattern | frontend `SettingsPage`, `UsersPage` | Admin-gated pages + API clients |
| `ConfirmDialog` | frontend `components/ConfirmDialog.tsx` | Destructive pack actions (uninstall/disable) |
| Multipart + zip MIME allowlist | `docuforge.storage.allowed-*` | ZIP already listed; size limits need pack-specific config |

---

## 3. DATABASE CURRENT STATE

**Flyway:** `backend/src/main/resources/db/migration` — **V1 … V8** (next free: **V9+**).  
PRD example numbers `V20+` must **not** be used blindly (§59).

| Migration | Content |
|-----------|---------|
| V1 | `companies`, `roles`, `users`, `user_roles`, `templates`, `template_versions`, `template_variables`, `generated_documents`, `batch_jobs`, `audit_logs` + role seeds |
| V2 | `refresh_tokens` |
| V3 | document lineage columns on `generated_documents` |
| V4 | `ai_requests` |
| V5 | `batch_items` + batch job storage/mapping columns |
| V6 | `email_deliveries` |
| V7 | `application_settings` |
| V8 | `password_reset_tokens` |

**Core template/document columns (today):**

- `templates`: `company_id`, `code`, `name`, `description`, `category`, `status`, `current_version_id` — **no** `origin`, **no** `source_pack_id`
- `template_versions`: `version_number`, `storage_key`, `checksum` (hex SHA-256, **no** `sha256:` prefix) — **no** `source_pack_version_id`, **no** `source_template_code`
- `template_variables`: typed fields + JSONB `configuration`
- `generated_documents`: `template_id`, `template_version_id`, lineage — **no** `pack_id` / `pack_version_id`

**Absent (PRD §§44–53):**  
`business_packs`, `business_pack_versions`, `business_pack_templates`, `business_pack_prompts`, `business_pack_files`, `business_pack_installations`, `pack_import_jobs`.

**Roles seeded:** `ADMIN`, `EDITOR`, `USER`, `VIEWER`.

---

## 4. BACKEND CURRENT STATE

**Packages under `ai.docuforge`:**  
`admin`, `ai`, `audit`, `auth`, `batch`, `common`, `config`, `dashboard`, `document`, `domain`, `email`, `form`, `privacy`, `security`, `settings`, `storage`, `template`, `web`.

**No** `businesspack` / `pack` package.

**Key APIs (existing):**

| Area | Base path |
|------|-----------|
| Auth | `/api/v1/auth` |
| Templates | `/api/v1/templates` |
| Variables | `/api/v1/template-versions/{id}/variables` |
| Forms | `/api/v1/template-versions/{id}/form-schema` |
| Documents | `/api/v1/documents` |
| AI | `/api/v1/ai` |
| Admin settings/users | `/api/v1/admin/settings`, `/api/v1/admin/users` |
| Privacy | `/api/v1/privacy` |
| Audit | `/api/v1/audit` |

**Missing:** entire `/api/v1/admin/business-packs/**` surface (§78–97).

**OpenAPI:** `OpenApiConfig` + springdoc (`/v3/api-docs`, `/swagger-ui.html`), JWT bearer scheme.

**Config namespaces:** `docuforge.storage|jwt|auth.cookies|pdf|ai|batch|antivirus|rate-limit|bootstrap|mail` — **no** `docuforge.packs` (§178).

**App version:** artifact `0.1.0-SNAPSHOT` — relevant for SemVer compatibility checks (`minimumDocuForgeVersion`).

---

## 5. FRONTEND CURRENT STATE

**Structure:** `src/{api,auth,components,i18n,lib,pages}` — ~16 pages, no pack modules.

**Auth:** in-memory access token + refresh cookie (`credentials: "include"`); `Protected` gate is token-only (pages enforce admin).

**Roles in UI:** `isAdmin`, `canEditTemplates`; Settings/Users admin-only; no `/admin/*` route prefix (routes are `/settings`, `/users`, `/templates`, …).

**Templates UI:** list/create/upload/activate/archive; `FormPage` + `DynamicForm` for generation.

**i18n:** `fr` (default), `pt` only.

**State management:** TanStack Query + local React state. **Zustand is not installed** despite Core PRD / Business Pack PRD §128 assuming it.

**Missing routes (§107):**  
`/admin/business-packs`, import wizard, pack detail/versions/templates.

---

## 6. STORAGE CURRENT STATE

| Item | Current |
|------|---------|
| Abstraction | `StorageProvider` (`store` / `read` / `delete` / `exists`) |
| Implementation | `LocalStorageProvider` only |
| Categories | `TEMPLATES`, `GENERATED`, `TEMPORARY` |
| Key format | `{category}/{uuid}.{ext}` enforced by `StoragePathGuard.SAFE_KEY` |
| Antivirus | Optional ClamAV on store |
| Allowlist | includes `docx`, `pdf`, `csv`, `zip` (+ MIME types) |
| Default max upload | `MAX_UPLOAD_SIZE_MB` default **25** (packs PRD wants up to **100** MB configurable) |

**Conflict with PRD §§75–76:** nested keys such as  
`packs/com.docuforge.pack.artisan/1.0.0/...` or `packs/archives/{packId}/{version}/{uuid}.zip`  
**cannot** pass the current `SAFE_KEY` regex (`^[a-z]+/[uuid].[ext]$`).

MinIO Compose profile exists; **no** S3 `StorageProvider` implementation.

---

## 7. AI CURRENT STATE

| Item | Current |
|------|---------|
| Interface | `AIProvider.generate(AIRequest)` |
| Impl | `OllamaAIProvider` when `docuforge.ai.enabled=true` (default **false**) |
| Operations | `REWRITE`, `FORMALIZE`, `SUMMARIZE`, `GENERATE_PARAGRAPH` |
| Prompts | `PromptCatalog` loads **classpath** FR templates (`PROMPT_VERSION = v1-fr`) |
| Persistence | `ai_requests` stores metadata (operation, tokens, etc.), not pack prompt catalog |

**Gap:** no DB-backed pack prompt store; no wiring of pack `prompt_code` into `AIProvider`; prompts are not versioned per pack.

---

## 8. SECURITY CURRENT STATE

| Area | Status |
|------|--------|
| AuthN | JWT HS256 + optional httpOnly cookies; refresh tokens; password reset |
| AuthZ | Method security `@PreAuthorize`; roles ADMIN/EDITOR/USER/VIEWER |
| CSRF | Disabled (stateless / cookie SameSite Lax same-origin via nginx) |
| Upload hardening | Filename sanitization, extension/MIME allowlist, size limit, optional ClamAV |
| ZIP security | **No** zip-bomb / zip-slip / symlink inspector for pack archives yet |
| Rate limit | Login / AI / forgot-password |
| Production safety | `ProductionSafetyValidator` for insecure defaults |
| Tenancy | Company-scoped queries + JWT `companyId` |

Pack admin APIs must be **ADMIN-only**; catalog read may align with existing template read roles (§102).

---

## 9. TEST CURRENT STATE

**Backend:** ~25 test classes under `backend/src/test/java/ai/docuforge/` — templates, forms, documents, PDF, storage, auth, admin, audit, AI, batch, schema migration. Testcontainers available via POM.

**Frontend:** `tsc` lint/build only — no Vitest/Jest pack tests.

**E2E:** `tests/e2e` Playwright present for core flows — **no** pack scenarios.

**CI:** `.github/workflows/ci.yml` — `mvn verify`, frontend build, compose config validation.

**Missing (§141–153, §184–189):** archive security tests, DBPF fixtures, atomic install tests, multi-tenant pack isolation, round-trip export/import.

---

## 10. GAPS AGAINST PRD

| PRD area | Gap |
|----------|-----|
| DBPF-1 format + docs | No `docs/business-packs/` specs (only this analysis + root PRD) |
| Domain tables / entities | All pack tables missing |
| Template provenance | No `origin` / pack FKs; documents lack pack lineage |
| Import/validate/install pipeline | Import + validate delivered (Phase 9); install still missing |
| Archive security | Missing Commons Compress inspector, zip limits, symlink reject |
| Manifest JSON Schema | Missing networknt validator + schemas |
| SemVer | `com.vdurmont:semver4j:3.1.0` via `PackCompatibilityService`; platform version `docuforge.packs.platform-version` |
| MIME detection | No Apache Tika |
| Storage paths | Flat keys incompatible with pack layout |
| Pack prompts | No install path into AI |
| Reserved `system.*` vars | No generator injection of system namespace (§35) |
| Variable type `INTEGER` | App uses `NUMBER` |
| Variable key regex | App allows flat keys; PRD prefers dotted keys |
| Checksum format | Hex vs `sha256:<hex>` |
| Frontend packs UI + Zustand | Missing |
| Feature flag `docuforge.packs` | Missing |
| Audit `PACK_*` | Missing |
| Official demo pack Artisan | Missing |
| Job locking | PRD assumes SKIP LOCKED; batch has `@Async` + columns but **no** `FOR UPDATE SKIP LOCKED` claim query yet |
| Package name | `com.docuforge.pack` vs `ai.docuforge.*` |

---

## 11. REQUIRED DATABASE CHANGES

*(Conceptual — do not apply in Phase 0. Suggest starting at **V9**.)*

1. **New tables** (§§45–53):  
   `business_packs`, `business_pack_versions`, `business_pack_templates`, `business_pack_prompts`, `business_pack_files`, `business_pack_installations`, `pack_import_jobs`.

2. **Naming adaptation:** use **`company_id`** everywhere PRD says `organization_id` (FK → `companies`). Keep nullable for future global OFFICIAL catalog rows if needed (§104).

3. **Alter `templates`:** add `origin` (`SYSTEM`|`PACK`|`USER`), `source_pack_id` (nullable FK).

4. **Alter `template_versions`:** add `source_pack_version_id`, `source_template_code` (nullable).

5. **Alter `generated_documents`:** add nullable `pack_id`, `pack_version_id` (§13) for historical immutability.

6. **Indexes** per §58 (adapted to `company_id`).

7. **FK policy:** `ON DELETE RESTRICT` for historical links (§57); never cascade-delete versions referenced by documents.

8. **Backfill:** existing templates → `origin=USER` (or `SYSTEM` if seeded as product demos — open question).

---

## 12. REQUIRED BACKEND CHANGES

1. New package under **`ai.docuforge.businesspack`** (or `ai.docuforge.pack`) with subpackages: `api`, `application`, `domain`, `infrastructure`, `validation`, `archive`, `manifest`, `installation`, `export` — **not** `com.docuforge.pack` verbatim.

2. Services from §62 (split responsibilities; avoid god-service).

3. Dependencies to add when phases start:  
   `semver4j`, `json-schema-validator` (networknt), Commons Compress, Apache Tika (per PRD §199).

4. Extend `StorageCategory` (+ relax/extend `StoragePathGuard`) for pack archives/assets.

5. Config `docuforge.packs.*` + env `DOCUFORGE_PACKS_ENABLED` (§178–179).

6. REST `/api/v1/admin/business-packs/**` + extend `GET /api/v1/templates` filters `origin`, `packId` (§100–101).

7. Audit actions §105; i18n error keys for `PACK_*` codes (§69) in `messages_*.properties`.

8. Import job worker: prefer introducing shared SKIP LOCKED claim pattern (or document reuse of sync/`@Async` first for MVP small packs).

9. Installation must create Template/Version/Variable rows transactionally (§72–73); generator remains pack-agnostic (§4).

10. Optional later: inject `system.*` variables at generation time.

---

## 13. REQUIRED FRONTEND CHANGES

1. Routes (adapt to existing style): e.g. `/business-packs` or `/admin/business-packs` — decide consistency with `/settings` vs API `/admin` (§107, open question).

2. Nav entry for admins: “Packs métier” (§108).

3. Pages: list/cards, detail (overview/templates/versions), import wizard (upload → validate → review → install → success).

4. API client module + TanStack Query hooks (§129).

5. Template library filters: origin / pack (§123).

6. i18n FR/PT strings for all pack UI.

7. **Zustand:** introduce when wizard/filter global UI state is needed (§128), or use local state initially and add Zustand deliberately (PRD assumes it already exists — it does not).

8. Rebuild Docker frontend image when shipping UI.

---

## 14. COMPATIBILITY RISKS

| Risk | Detail |
|------|--------|
| `organization_id` vs `company_id` | Blind SQL from PRD breaks FKs |
| Package rename | Copy-paste `com.docuforge.*` breaks conventions |
| `INTEGER` vs `NUMBER` | Manifest metadata mapping required |
| Variable key regex | Pack dotted keys vs looser existing rules — may reject or accept differently |
| Checksum prefix | `sha256:` in DBPF vs raw hex in DB today |
| Storage key regex | Blocks PRD nested paths without deliberate redesign |
| DocuForge SemVer | `0.1.0` vs packs declaring `minimumDocuForgeVersion: 1.0.0` |
| Zustand / SKIP LOCKED / Tika | PRD treats as “already decided”; not all present in code |
| Route prefix `/admin` | Frontend rarely uses `/admin` path segment |
| Template code collisions | Pack codes vs existing company codes (§171) |

---

## 15. MIGRATION RISKS

| Risk | Mitigation |
|------|------------|
| Editing applied Flyway scripts | Forbidden (§60) — only new V9+ |
| Backfill of `origin` | Explicit default for existing rows |
| Adding NOT NULL columns without default | Use nullable first or backfill then constrain |
| Historical documents without pack FKs | Nullable `pack_*` on `generated_documents` |
| Long transactions on large installs | Staging files outside TX; compensate on failure (§73) |
| Orphan storage files after rollback | Cleanup job / retention (§133) |
| Unique `(company_id, code)` collisions on install | Pre-check + clear error `PACK_*` / collision policy |

---

## 16. SECURITY RISKS

| Risk | Notes |
|------|--------|
| Zip bomb / zip slip / symlinks | Must implement before any extract (§19–21) |
| Malware in ZIP | ClamAV scan of full archive (§65); AV optional in Compose |
| Path traversal via storage keys | Current guard is strict; relaxing keys needs new safe rules |
| Prompt injection / privilege via pack prompts | Prompts are data only (§38); never execute as code |
| Cross-tenant pack leakage | Enforce `company_id` on custom packs & installations (§103) |
| Oversized uploads | Separate pack limits vs 25 MB global default |
| Trusting Content-Type | Need Tika / OOXML validation (§137–138) |
| Macro DOCM | Reject (§139) |
| Force-delete history | Explicitly forbidden (§96) |

---

## 17. PROPOSED PACKAGE STRUCTURE

Align with existing `ai.docuforge` root (adapt PRD §61):

```text
ai.docuforge.businesspack
├── api                 # Controllers, DTOs, OpenAPI annotations
├── application         # Orchestration use-cases / facades
├── domain              # Entities, enums, repositories
├── infrastructure      # Persistence adapters, scheduling, storage helpers
├── archive             # PackArchiveInspector (zip security)
├── manifest            # Parser, JSON Schema, semantic validation
├── validation          # Pipeline, report, checksum, template/prompt validators
├── installation        # Install / update / enable / uninstall
└── export              # DBPF-1 export builder
```

**Config:** `ai.docuforge.config.PackProperties` (`docuforge.packs`).  
**Domain entities** may live under `ai.docuforge.domain.businesspack` to match existing `domain.template` / `domain.document` layout — prefer consistency with the repo over a pure hexagonal folder if conflict arises.

---

## 18. PROPOSED IMPLEMENTATION ORDER

Follow PRD §192 strictly (no big-bang):

| Phase | Focus | Stop when |
|-------|--------|-----------|
| **1** | DBPF-1.md, JSON Schemas, fixtures | Specs only, no DB |
| **2** | `business_packs` / `business_pack_versions` + entities | Domain + tests |
| **3** | Relations + template origin columns + import jobs | Schema complete |
| **4** | Secure `PackArchiveInspector` | No install |
| **5** | Manifest parser/validator | No install |
| **6** | Checksums + SemVer compatibility | No install |
| **7** | DOCX / placeholder / metadata validation | No install |
| **8** | Full `PackValidationService` + report | No install |
| **9** | Import API + staging + retention | Validate only |
| **10** | Atomic installation engine | Install works |
| **11** | Query APIs | List/detail |
| **12** | Enable/disable | History preserved |
| **13–14** | Frontend list/detail + import wizard | Admin UX |
| **15** | Update Engine | Detection, diff, breaking, SUPERSEDED |
| **16** | Update UI | Wizard review + confirmation |
| **17** | Duplicate Pack Template | PACK → USER independence |
| **18** | Uninstall (logical) | Soft UNINSTALLED + historical docs |
| **19** | Export | DBPF-1 ZIP + recalculated checksums |
| **20** | Round Trip | export → uninstall → import → validate → install |
| **21** | Artisan Demo Pack | `com.docuforge.pack.artisan-demo` |
| **22** | Security Hardening | ZIP Slip/bomb, MIME, RBAC, tenant |
| **23** | E2E | Playwright §188 principal + §189 update |
| **24** | Documentation | Creator/admin/DBPF/security/versioning/troubleshooting |
| **25** | Release readiness | Builds, suites, `BUSINESS_PACK_RELEASE_REPORT.md` |

**Prerequisite spike (before or at start of Phase 4/9):** redesign storage keys/categories for packs without breaking existing `templates/{uuid}.docx` keys.

---

## 19. OPEN QUESTIONS

1. **Map `organization_id` → `company_id` exclusively?** (Recommended: yes.)
2. **Official packs:** global rows (`company_id` NULL) + per-company `business_pack_installations`, or always company-local copies?
3. **Frontend route prefix:** `/business-packs` (current UI style) vs `/admin/business-packs` (PRD literal)?
4. **Java package name:** `businesspack` vs `pack`?
5. **Existing template `origin` backfill:** `USER` vs `SYSTEM` for demo templates under `templates/demo`?
6. **Variable type mapping:** treat DBPF `INTEGER` as existing `NUMBER`?
7. **Tighten variable keys** to PRD dotted regex, or accept current looser rules for MVP?
8. **Checksum persistence:** store with `sha256:` prefix or normalize on compare?
9. **DocuForge product version** for compatibility: keep `0.1.0` or introduce a separate `docuforge.packs.platform-version` / bump to `1.0.0`?
10. **Storage key strategy:** nested pack paths vs keep flat UUID keys + DB `logical_path` in `business_pack_files`?
11. **Introduce Zustand now** or defer until wizard complexity requires it?
12. **Import jobs:** implement SKIP LOCKED in Phase 9, or synchronous validate for small ZIPs first (§81)?
13. **`system.*` injection:** required for MVP packs or Phase later?
14. **Pack prompts vs `AiOperation` enum:** free-form codes only, or map to existing operations?
15. **Should generated_documents.pack_* be mandatory** when template.origin=PACK, or always nullable?

---

## 20. PHASE 1 READINESS

**Phase 1–25:** delivered (through release readiness / `BUSINESS_PACK_RELEASE_REPORT.md`).

**Business Pack MVP implementation track:** **COMPLETE** for authorized phases 0–25.

**Blockers for further product work:** none from this module; next work requires explicit authorization (marketplace / PKI / etc. remain out of scope per PRD §6).

**Explicitly out of scope until later:** marketplace, payments, PKI signatures, remote auto-update (§6).

---

## Appendix A — Naming map (PRD → existing)

| PRD term | Existing DocuForge |
|----------|-------------------|
| Organization | `Company` / `company_id` |
| Template engine | `template.*` + `document.engine.*` |
| `com.docuforge.pack` | Propose `ai.docuforge.businesspack` |
| StorageProvider | `ai.docuforge.storage.StorageProvider` |
| AIProvider | `ai.docuforge.ai.AIProvider` |
| Error envelope | `ErrorResponse` |
| V20 migrations | Use **V9+** |
| Zustand | Not present — add when needed |
| SKIP LOCKED jobs | Pattern intended; not fully implemented in batch |

## Appendix B — Inventory snapshot

- Backend Java sources: ~177 under `ai.docuforge`
- Flyway: V1–V11 (pack core + relations + import staging)
- Business pack code: Phase 2–17 (`ai.docuforge.domain.businesspack.*`, `ai.docuforge.businesspack.*`, frontend `/business-packs` + import/update/duplicate)
