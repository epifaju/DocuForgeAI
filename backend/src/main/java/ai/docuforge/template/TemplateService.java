package ai.docuforge.template;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateOrigin;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StoragePathGuard;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import ai.docuforge.template.dto.TemplateCreateRequest;
import ai.docuforge.template.dto.TemplateDuplicateRequest;
import ai.docuforge.template.dto.TemplateResponse;
import ai.docuforge.template.dto.TemplateUpdateRequest;
import ai.docuforge.template.dto.TemplateVersionResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TemplateService {

    private static final byte[] DOCX_ZIP_MAGIC = new byte[] {0x50, 0x4B, 0x03, 0x04};
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final CompanyRepository companyRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final StorageProvider storageProvider;
    private final AuditService auditService;
    private final TemplateVariableService templateVariableService;

    public TemplateService(
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            CompanyRepository companyRepository,
            GeneratedDocumentRepository generatedDocumentRepository,
            StorageProvider storageProvider,
            AuditService auditService,
            TemplateVariableService templateVariableService
    ) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.companyRepository = companyRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.storageProvider = storageProvider;
        this.auditService = auditService;
        this.templateVariableService = templateVariableService;
    }

    @Transactional
    public TemplateResponse create(DocuForgePrincipal principal, TemplateCreateRequest request) {
        UUID companyId = principal.getCompanyId();
        if (templateRepository.existsByCompanyIdAndCode(companyId, request.code())) {
            throw conflict("Un template avec ce code existe déjà.");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> notFound("Société introuvable"));

        Template template = new Template();
        template.setCompany(company);
        template.setCode(request.code());
        template.setName(request.name());
        template.setDescription(request.description());
        template.setCategory(request.category());
        template.setStatus(TemplateStatus.DRAFT);
        template.setOrigin(TemplateOrigin.USER);
        template.setCreatedBy(principal.getUserId());
        template = templateRepository.save(template);

        writeAudit(principal, AuditActions.TEMPLATE_CREATED, template.getId());
        return TemplateMapper.toResponse(template);
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateResponse> list(
            DocuForgePrincipal principal,
            TemplateStatus status,
            String q,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Template> result = templateRepository.search(
                principal.getCompanyId(),
                status,
                q,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        List<TemplateResponse> items = result.getContent().stream().map(TemplateMapper::toResponse).toList();
        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(DocuForgePrincipal principal, UUID templateId) {
        return TemplateMapper.toResponse(requireTemplateWithCurrent(principal.getCompanyId(), templateId));
    }

    @Transactional
    public TemplateResponse update(DocuForgePrincipal principal, UUID templateId, TemplateUpdateRequest request) {
        Template template = requireTemplateWithCurrent(principal.getCompanyId(), templateId);
        assertNotPackImmutable(template);
        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            throw conflict("error.template.archived_readonly");
        }
        if (templateRepository.existsByCompanyIdAndCodeAndIdNot(
                principal.getCompanyId(), request.code(), templateId)) {
            throw conflict("error.template.code_exists");
        }
        template.setCode(request.code());
        template.setName(request.name());
        template.setDescription(request.description());
        template.setCategory(request.category());
        template = templateRepository.save(template);
        writeAudit(principal, AuditActions.TEMPLATE_UPDATED, template.getId());
        return TemplateMapper.toResponse(template);
    }

    /**
     * Creates an independent USER copy of a template (PRD §§125–127).
     * Pack provenance is cleared so pack updates never collide with the copy.
     */
    @Transactional
    public TemplateResponse duplicate(
            DocuForgePrincipal principal,
            UUID templateId,
            TemplateDuplicateRequest request
    ) {
        Template source = requireTemplateWithCurrent(principal.getCompanyId(), templateId);
        TemplateVersion sourceVersion = source.getCurrentVersion();
        if (sourceVersion == null) {
            throw conflict("error.template.no_current_version");
        }
        if (!storageProvider.exists(sourceVersion.getStorageKey())) {
            throw conflict("error.template.current_docx_missing");
        }

        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> notFound("error.company.not_found"));
        String code = allocateCopyCode(principal.getCompanyId(), source.getCode());

        byte[] bytes;
        try (InputStream in = storageProvider.read(sourceVersion.getStorageKey())) {
            bytes = in.readAllBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.template.read_failed", ex);
        }

        String checksum;
        StoredFile stored;
        try (InputStream in = new ByteArrayInputStream(bytes);
             DigestInputStream digestStream = new DigestInputStream(in, sha256())) {
            stored = storageProvider.store(
                    StorageCategory.TEMPLATES,
                    sourceVersion.getOriginalFilename(),
                    DOCX_CONTENT_TYPE,
                    digestStream,
                    bytes.length
            );
            checksum = HexFormat.of().formatHex(digestStream.getMessageDigest().digest());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.template.store_failed", ex);
        }

        try {
            Template copy = new Template();
            copy.setCompany(company);
            copy.setCode(code);
            copy.setName(request.name().trim());
            copy.setDescription(source.getDescription());
            copy.setCategory(source.getCategory());
            copy.setStatus(TemplateStatus.DRAFT);
            copy.setOrigin(TemplateOrigin.USER);
            copy.setSourcePack(null);
            copy.setCreatedBy(principal.getUserId());
            copy = templateRepository.save(copy);

            TemplateVersion version = new TemplateVersion();
            version.setTemplate(copy);
            version.setVersionNumber(1);
            version.setOriginalFilename(stored.originalFilename());
            version.setStorageKey(stored.storageKey());
            version.setChecksum(checksum);
            version.setSourcePackVersion(null);
            version.setSourceTemplateCode(null);
            version.setCreatedBy(principal.getUserId());
            version = templateVersionRepository.save(version);
            templateVariableService.copyFromVersion(sourceVersion.getId(), version);

            copy.setCurrentVersion(version);
            copy = templateRepository.save(copy);

            auditService.record(
                    principal,
                    AuditActions.TEMPLATE_CREATED,
                    "TEMPLATE",
                    copy.getId().toString(),
                    "SUCCESS",
                    Map.of(
                            "duplicatedFrom", source.getId().toString(),
                            "sourceOrigin", source.getOrigin().name(),
                            "code", copy.getCode()
                    )
            );
            return TemplateMapper.toResponse(copy);
        } catch (RuntimeException ex) {
            try {
                if (storageProvider.exists(stored.storageKey())) {
                    storageProvider.delete(stored.storageKey());
                }
            } catch (Exception ignored) {
                // best effort compensation
            }
            throw ex;
        }
    }

    @Transactional
    public void delete(DocuForgePrincipal principal, UUID templateId) {
        Template template = requireTemplate(principal.getCompanyId(), templateId);
        assertNotPackImmutable(template);
        if (template.getStatus() != TemplateStatus.DRAFT) {
            throw conflict("error.template.delete_draft_only");
        }
        if (generatedDocumentRepository.existsByTemplateId(templateId)) {
            throw conflict("error.template.has_documents");
        }

        List<TemplateVersion> versions = templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        template.setCurrentVersion(null);
        templateRepository.saveAndFlush(template);

        for (TemplateVersion version : versions) {
            storageProvider.delete(version.getStorageKey());
            templateVersionRepository.delete(version);
        }
        templateRepository.delete(template);
        writeAudit(principal, AuditActions.TEMPLATE_DELETED, templateId);
    }

    @Transactional
    public TemplateVersionResponse addVersion(
            DocuForgePrincipal principal,
            UUID templateId,
            MultipartFile file,
            boolean setAsCurrent
    ) {
        Template template = requireTemplateWithCurrent(principal.getCompanyId(), templateId);
        assertNotPackImmutable(template);
        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            throw conflict("error.template.version_archived");
        }
        if (file == null || file.isEmpty()) {
            throw badRequest("error.template.docx_required");
        }

        String originalFilename = file.getOriginalFilename() == null ? "template.docx" : file.getOriginalFilename();
        StoragePathGuard.sanitizeOriginalFilename(originalFilename);
        validateDocxFilename(originalFilename);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.template.read_failed", ex);
        }
        if (bytes.length < 4 || !Arrays.equals(Arrays.copyOf(bytes, 4), DOCX_ZIP_MAGIC)) {
            throw badRequest("error.template.docx_invalid");
        }

        // Parse before store so corrupt OOXML fails without orphan files (Phase 7).
        var detected = templateVariableService.createFromDocxPreview(bytes);

        String checksum;
        StoredFile stored;
        try (InputStream in = new ByteArrayInputStream(bytes);
             DigestInputStream digestStream = new DigestInputStream(in, sha256())) {
            stored = storageProvider.store(
                    StorageCategory.TEMPLATES,
                    originalFilename,
                    DOCX_CONTENT_TYPE,
                    digestStream,
                    bytes.length
            );
            checksum = HexFormat.of().formatHex(digestStream.getMessageDigest().digest());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.template.store_failed", ex);
        }

        int nextVersion = templateVersionRepository.findMaxVersionNumber(templateId) + 1;
        TemplateVersion version = new TemplateVersion();
        version.setTemplate(template);
        version.setVersionNumber(nextVersion);
        version.setOriginalFilename(stored.originalFilename());
        version.setStorageKey(stored.storageKey());
        version.setChecksum(checksum);
        version.setCreatedBy(principal.getUserId());
        version = templateVersionRepository.save(version);
        templateVariableService.persistDetected(version, detected);

        if (setAsCurrent || template.getCurrentVersion() == null) {
            template.setCurrentVersion(version);
            templateRepository.save(template);
        }

        writeAudit(principal, AuditActions.TEMPLATE_VERSION_CREATED, template.getId());
        return TemplateMapper.toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public List<TemplateVersionResponse> listVersions(DocuForgePrincipal principal, UUID templateId) {
        requireTemplate(principal.getCompanyId(), templateId);
        return templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId).stream()
                .map(TemplateMapper::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateVersionResponse getVersion(DocuForgePrincipal principal, UUID templateId, int versionNumber) {
        requireTemplate(principal.getCompanyId(), templateId);
        TemplateVersion version = templateVersionRepository
                .findByTemplateIdAndVersionNumber(templateId, versionNumber)
                .orElseThrow(() -> notFound("Version introuvable"));
        return TemplateMapper.toVersionResponse(version);
    }

    @Transactional
    public TemplateResponse activate(DocuForgePrincipal principal, UUID templateId) {
        Template template = requireTemplateWithCurrent(principal.getCompanyId(), templateId);
        if (template.getCurrentVersion() == null) {
            throw conflict("Impossible d'activer un template sans version DOCX.");
        }
        if (!storageProvider.exists(template.getCurrentVersion().getStorageKey())) {
            throw conflict("Le fichier DOCX de la version courante est introuvable.");
        }
        template.setStatus(TemplateStatus.ACTIVE);
        template = templateRepository.save(template);
        writeAudit(principal, AuditActions.TEMPLATE_ACTIVATED, template.getId());
        return TemplateMapper.toResponse(template);
    }

    @Transactional
    public TemplateResponse archive(DocuForgePrincipal principal, UUID templateId) {
        Template template = requireTemplateWithCurrent(principal.getCompanyId(), templateId);
        template.setStatus(TemplateStatus.ARCHIVED);
        template = templateRepository.save(template);
        writeAudit(principal, AuditActions.TEMPLATE_ARCHIVED, template.getId());
        return TemplateMapper.toResponse(template);
    }

    private Template requireTemplateWithCurrent(UUID companyId, UUID templateId) {
        return templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId)
                .orElseThrow(() -> notFound("error.template.not_found"));
    }

    private Template requireTemplate(UUID companyId, UUID templateId) {
        return templateRepository.findByIdAndCompanyId(templateId, companyId)
                .orElseThrow(() -> notFound("error.template.not_found"));
    }

    private void assertNotPackImmutable(Template template) {
        if (template.getOrigin() == TemplateOrigin.PACK) {
            throw conflict("error.template.pack_immutable");
        }
    }

    /**
     * Builds a unique code under company constraints ({@code SOURCE_COPY}, {@code SOURCE_COPY2}, …).
     */
    private String allocateCopyCode(UUID companyId, String sourceCode) {
        String sanitized = sourceCode == null ? "TEMPLATE" : sourceCode.trim();
        if (sanitized.isEmpty()) {
            sanitized = "TEMPLATE";
        }
        String base = sanitized + "_COPY";
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        if (!templateRepository.existsByCompanyIdAndCode(companyId, base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String suffix = String.valueOf(i);
            int maxBase = Math.max(1, 100 - suffix.length());
            String candidate = (base.length() > maxBase ? base.substring(0, maxBase) : base) + suffix;
            if (!templateRepository.existsByCompanyIdAndCode(companyId, candidate)) {
                return candidate;
            }
        }
        throw conflict("error.template.code_exists");
    }

    private void validateDocxFilename(String originalFilename) {
        String name = originalFilename.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".docx")) {
            throw badRequest("error.template.docx_extension");
        }
    }

    private void writeAudit(DocuForgePrincipal principal, String action, UUID entityId) {
        auditService.recordSuccess(principal, action, "TEMPLATE", entityId);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
