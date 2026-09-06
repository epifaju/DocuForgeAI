# Phase 6 — Checksums & Compatibility (completion notes)

**Completed:** 2026-09-06  
**Scope:** SHA-256 verification helpers + SemVer platform compatibility (no ZIP extract, no install)

## Delivered

| Artifact | Path |
|----------|------|
| Checksum validator | `ai.docuforge.businesspack.checksum.PackChecksumValidator` |
| Compatibility service | `ai.docuforge.businesspack.compatibility.PackCompatibilityService` |
| SemVer library | `com.vdurmont:semver4j:3.1.0` |
| Platform version config | `docuforge.packs.platform-version` / `PACK_PLATFORM_VERSION` |
| Tests | `PackChecksumValidatorTest`, `PackCompatibilityServiceTest` |

## Checksums (PRD §§41–42)

- Digest: SHA-256
- DBPF declared format: `sha256:<64 hex>`
- Also accepts raw 64-hex (DB `template_versions.checksum` adaptation)
- Compare is case-insensitive on hex
- Mismatch → `PACK_CHECKSUM_MISMATCH` (ERROR) with `file`
- Missing declared checksum for a provided path → warning `PACK_MANIFEST_CHECKSUM_UNDECLARED`
- Does **not** raise `PACK_FILE_MISSING` (Phase 8)

## Compatibility (PRD §§70–71)

- Compares configured platform SemVer to `minimumDocuForgeVersion` / `maximumDocuForgeVersion`
- `maximumDocuForgeVersion` supports exact SemVer or major range `N.x` (NPM satisfies via semver4j)
- Failure → `PACK_INCOMPATIBLE_DOCUFORGE_VERSION`
- Helpers: `isDowngrade`, `downgradeNotAllowedIssue` (`PACK_DOWNGRADE_NOT_ALLOWED`), `classifyUpdate` (MAJOR/MINOR/PATCH)
- Default platform version: `0.1.0` (strips `-SNAPSHOT`)

## Explicit non-goals

- ZIP file existence / extraction (Phase 8)
- Template DOCX validation (Phase 7)
- Full `PackValidationService` pipeline assembly (Phase 8)
- Install / admin rollback UX (Phase 10+)

## Migrations

None.

## Verification

```bash
cd backend
mvn "-Dtest=PackChecksumValidatorTest,PackCompatibilityServiceTest,PackArchiveInspectorTest,PackManifestValidatorTest" test
```
