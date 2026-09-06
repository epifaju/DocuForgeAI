# Phase 17 — Duplicate Pack Template (completion notes)

**Completed:** 2026-09-06  
**Scope:** PACK → duplicate → USER with independence tests (PRD §§125–127)

## Delivered

| Artifact | Path |
|----------|------|
| API | `POST /api/v1/templates/{id}/duplicate` |
| Request DTO | `TemplateDuplicateRequest` |
| Service | `TemplateService.duplicate` + pack immutability guards |
| Variables copy | `TemplateVariableService.copyFromVersion` |
| Response | `TemplateResponse.origin` / `sourcePackId` |
| UI | Dupliquer on pack detail templates tab |
| Test | `PackTemplateDuplicateApiTest` |

## Behaviour

1. Pack templates (`origin=PACK`) cannot be updated / versioned / deleted / have variables edited.
2. Duplicate copies current DOCX via `StorageProvider` (new key), copies variables, creates `origin=USER`, `sourcePack=null`, `DRAFT`, code `{CODE}_COPY` (+ suffix if needed).
3. Copy is editable independently; pack template unchanged.
4. Audit: `TEMPLATE_CREATED` with `duplicatedFrom` metadata.

## Explicit non-goals

- Uninstall / export
- Template library badges (§123) beyond origin fields
- Zustand

## Migrations

- None.

## Verification

```bash
cd backend
mvn -Dtest=PackTemplateDuplicateApiTest,TemplateManagementTest test

cd frontend
npm run lint
npm run build
```
