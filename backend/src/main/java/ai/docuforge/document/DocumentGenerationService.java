package ai.docuforge.document;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.config.PdfProperties;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.document.DocumentStatus;
import ai.docuforge.domain.document.GeneratedDocument;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import ai.docuforge.document.dto.DocumentGenerateRequest;
import ai.docuforge.document.dto.DocumentNewVersionRequest;
import ai.docuforge.document.dto.GeneratedDocumentResponse;
import ai.docuforge.document.engine.DocumentGenerator;
import ai.docuforge.document.pdf.PdfConversionException;
import ai.docuforge.document.pdf.PdfConverter;
import ai.docuforge.form.FormDataValidator;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import ai.docuforge.common.api.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentGenerationService {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME = "application/pdf";

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateVariableRepository templateVariableRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final CompanyRepository companyRepository;
    private final FormDataValidator formDataValidator;
    private final DocumentGenerator documentGenerator;
    private final StorageProvider storageProvider;
    private final DocumentReferenceService documentReferenceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PdfProperties pdfProperties;
    private final ObjectProvider<PdfConverter> pdfConverter;
    private final UserAccountRepository userAccountRepository;

    public DocumentGenerationService(
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateVariableRepository templateVariableRepository,
            GeneratedDocumentRepository generatedDocumentRepository,
            CompanyRepository companyRepository,
            FormDataValidator formDataValidator,
            DocumentGenerator documentGenerator,
            StorageProvider storageProvider,
            DocumentReferenceService documentReferenceService,
            AuditService auditService,
            ObjectMapper objectMapper,
            PdfProperties pdfProperties,
            ObjectProvider<PdfConverter> pdfConverter,
            UserAccountRepository userAccountRepository
    ) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.templateVariableRepository = templateVariableRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.companyRepository = companyRepository;
        this.formDataValidator = formDataValidator;
        this.documentGenerator = documentGenerator;
        this.storageProvider = storageProvider;
        this.documentReferenceService = documentReferenceService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.pdfProperties = pdfProperties;
        this.pdfConverter = pdfConverter;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(noRollbackFor = PdfConversionException.class)
    public GeneratedDocumentResponse generate(DocuForgePrincipal principal, DocumentGenerateRequest request) {
        GenerationContext ctx = prepare(principal, request);
        formDataValidator.validateOrThrow(ctx.variables(), request.data());

        byte[] stamped = stamp(ctx.version(), request.data());
        StoredFile stored = storeGenerated(stamped, ctx.template().getCode());
        String checksum = sha256Hex(stamped);

        GeneratedDocument document = persist(
                principal,
                ctx.template(),
                ctx.version(),
                resolveTitle(request, ctx.template()),
                request.data(),
                stored.storageKey(),
                checksum,
                DocumentStatus.GENERATED,
                null,
                null,
                1
        );
        writeAudit(principal, AuditActions.DOCUMENT_GENERATED, document.getId());

        if (pdfProperties.enabled()) {
            convertToPdf(principal, document, stamped, ctx.template().getCode());
        }

        return toResponse(document, resolveCreatorName(document.getCreatedBy()));
    }

    /**
     * Batch-friendly generation entrypoint (same pipeline as {@link #generate}).
     */
    @Transactional(noRollbackFor = PdfConversionException.class)
    public GeneratedDocumentResponse generateMapped(
            DocuForgePrincipal principal,
            UUID templateId,
            UUID templateVersionId,
            Map<String, Object> data,
            String title
    ) {
        return generate(principal, new DocumentGenerateRequest(templateId, templateVersionId, title, data));
    }

    /**
     * Creates a new generated document as the next version of an existing one (PRD §28).
     * The source document is never mutated.
     */
    @Transactional(noRollbackFor = PdfConversionException.class)
    public GeneratedDocumentResponse createNewVersion(
            DocuForgePrincipal principal,
            UUID sourceDocumentId,
            DocumentNewVersionRequest request
    ) {
        GeneratedDocument source = requireDocument(principal.getCompanyId(), sourceDocumentId);
        Template template = source.getTemplate();
        TemplateVersion version = source.getTemplateVersion();

        List<TemplateVariable> variables = templateVariableRepository
                .findByTemplateVersionIdOrderByDisplayOrderAsc(version.getId());
        formDataValidator.validateOrThrow(variables, request.data());

        if (!storageProvider.exists(version.getStorageKey())) {
            throw conflict("Le fichier DOCX du template est introuvable.");
        }

        byte[] stamped = stamp(version, request.data());
        StoredFile stored = storeGenerated(stamped, template.getCode());
        String checksum = sha256Hex(stamped);

        UUID rootId = source.getRootDocumentId() != null ? source.getRootDocumentId() : source.getId();
        int nextVersion = generatedDocumentRepository.findMaxDocumentVersionNumber(rootId) + 1;
        String title = request.title() != null && !request.title().isBlank()
                ? request.title().trim()
                : source.getTitle();

        GeneratedDocument document = persist(
                principal,
                template,
                version,
                title,
                request.data(),
                stored.storageKey(),
                checksum,
                DocumentStatus.GENERATED,
                rootId,
                source.getId(),
                nextVersion
        );
        writeAudit(principal, AuditActions.DOCUMENT_VERSION_CREATED, document.getId());

        if (pdfProperties.enabled()) {
            convertToPdf(principal, document, stamped, template.getCode());
        }

        return toResponse(document, resolveCreatorName(document.getCreatedBy()));
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentResponse> listVersions(DocuForgePrincipal principal, UUID documentId) {
        GeneratedDocument document = requireDocument(principal.getCompanyId(), documentId);
        UUID rootId = document.getRootDocumentId() != null ? document.getRootDocumentId() : document.getId();
        List<GeneratedDocument> lineage = generatedDocumentRepository
                .findLineageByCompanyIdAndRootDocumentId(principal.getCompanyId(), rootId);
        Map<UUID, String> creatorNames = resolveCreatorNames(lineage);
        return lineage.stream()
                .map(doc -> toResponse(doc, creatorNames.get(doc.getCreatedBy())))
                .toList();
    }

    /**
     * Preview stamps without persisting a GeneratedDocument row (temporary file cleaned after read is caller's job;
     * here we return bytes only).
     */
    @Transactional(readOnly = true)
    public byte[] preview(DocuForgePrincipal principal, DocumentGenerateRequest request) {
        GenerationContext ctx = prepare(principal, request);
        formDataValidator.validateOrThrow(ctx.variables(), request.data());
        return stamp(ctx.version(), request.data());
    }

    @Transactional(readOnly = true)
    public PageResponse<GeneratedDocumentResponse> list(
            DocuForgePrincipal principal,
            DocumentStatus status,
            UUID templateId,
            UUID createdBy,
            Instant createdFrom,
            Instant createdTo,
            String q,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<GeneratedDocument> result = generatedDocumentRepository.findAll(
                GeneratedDocumentSpecs.filtered(
                        principal.getCompanyId(),
                        status,
                        templateId,
                        createdBy,
                        createdFrom,
                        createdTo,
                        q
                ),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Map<UUID, String> creatorNames = resolveCreatorNames(result.getContent());
        List<GeneratedDocumentResponse> items = result.getContent().stream()
                .map(doc -> toResponse(doc, creatorNames.get(doc.getCreatedBy())))
                .toList();
        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public GeneratedDocumentResponse get(DocuForgePrincipal principal, UUID documentId) {
        GeneratedDocument document = requireDocument(principal.getCompanyId(), documentId);
        return toResponse(document, resolveCreatorName(document.getCreatedBy()));
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadDocx(DocuForgePrincipal principal, UUID documentId) {
        GeneratedDocument document = requireDocument(principal.getCompanyId(), documentId);
        if (document.getDocxStorageKey() == null || document.getDocxStorageKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier DOCX introuvable.");
        }
        InputStream in = storageProvider.read(document.getDocxStorageKey());
        String filename = document.getReference() + ".docx";
        auditService.recordSuccess(
                principal,
                AuditActions.DOCUMENT_DOWNLOADED,
                "DOCUMENT",
                document.getId(),
                Map.of("format", "DOCX")
        );
        return new DocumentDownload(filename, DOCX_MIME, new InputStreamResource(in));
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadPdf(DocuForgePrincipal principal, UUID documentId, boolean preview) {
        GeneratedDocument document = requireDocument(principal.getCompanyId(), documentId);
        if (document.getPdfStorageKey() == null || document.getPdfStorageKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier PDF introuvable.");
        }
        InputStream in = storageProvider.read(document.getPdfStorageKey());
        String filename = document.getReference() + ".pdf";
        if (!preview) {
            auditService.recordSuccess(
                    principal,
                    AuditActions.DOCUMENT_DOWNLOADED,
                    "DOCUMENT",
                    document.getId(),
                    Map.of("format", "PDF")
            );
        }
        return new DocumentDownload(filename, PDF_MIME, new InputStreamResource(in));
    }

    private void convertToPdf(
            DocuForgePrincipal principal,
            GeneratedDocument document,
            byte[] stampedDocx,
            String templateCode
    ) {
        PdfConverter converter = pdfConverter.getIfAvailable();
        if (converter == null) {
            document.setStatus(DocumentStatus.FAILED);
            generatedDocumentRepository.saveAndFlush(document);
            throw new PdfConversionException("Conversion PDF activee mais aucun convertisseur disponible.");
        }

        document.setStatus(DocumentStatus.CONVERTING);
        generatedDocumentRepository.saveAndFlush(document);

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("docuforge-pdf-");
            Path source = workDir.resolve("source.docx");
            Files.write(source, stampedDocx);

            Path pdfPath = converter.convert(source);
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            StoredFile pdfStored = storeGeneratedPdf(pdfBytes, templateCode);

            document.setPdfStorageKey(pdfStored.storageKey());
            document.setStatus(DocumentStatus.COMPLETED);
            generatedDocumentRepository.saveAndFlush(document);
            writeAudit(principal, AuditActions.DOCUMENT_PDF_CONVERTED, document.getId());
        } catch (PdfConversionException ex) {
            document.setStatus(DocumentStatus.FAILED);
            generatedDocumentRepository.saveAndFlush(document);
            writeAudit(principal, AuditActions.DOCUMENT_PDF_FAILED, document.getId());
            throw ex;
        } catch (Exception ex) {
            document.setStatus(DocumentStatus.FAILED);
            generatedDocumentRepository.saveAndFlush(document);
            writeAudit(principal, AuditActions.DOCUMENT_PDF_FAILED, document.getId());
            throw new PdfConversionException("Conversion PDF echouee.", ex);
        } finally {
            deleteRecursivelyQuietly(workDir);
        }
    }

    private GenerationContext prepare(DocuForgePrincipal principal, DocumentGenerateRequest request) {
        Template template = templateRepository
                .findByIdAndCompanyIdWithCurrentVersion(request.templateId(), principal.getCompanyId())
                .orElseThrow(() -> notFound("Template introuvable"));
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw conflict("Seuls les templates ACTIVE peuvent generer un document.");
        }

        TemplateVersion version;
        if (request.templateVersionId() != null) {
            version = templateVersionRepository
                    .findByIdAndCompanyId(request.templateVersionId(), principal.getCompanyId())
                    .orElseThrow(() -> notFound("Version de template introuvable"));
            if (!version.getTemplate().getId().equals(template.getId())) {
                throw badRequest("La version ne correspond pas au template.");
            }
        } else {
            version = template.getCurrentVersion();
            if (version == null) {
                throw conflict("Le template n'a pas de version courante.");
            }
        }
        if (!storageProvider.exists(version.getStorageKey())) {
            throw conflict("Le fichier DOCX du template est introuvable.");
        }

        List<TemplateVariable> variables = templateVariableRepository
                .findByTemplateVersionIdOrderByDisplayOrderAsc(version.getId());
        return new GenerationContext(template, version, variables);
    }

    private byte[] stamp(TemplateVersion version, Map<String, Object> data) {
        try (InputStream in = storageProvider.read(version.getStorageKey())) {
            return documentGenerator.generate(in, data);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Lecture du template impossible.", ex);
        }
    }

    private StoredFile storeGenerated(byte[] stamped, String templateCode) {
        String filename = sanitizeFilename(templateCode) + "-generated.docx";
        return storageProvider.store(
                StorageCategory.GENERATED,
                filename,
                DOCX_MIME,
                new ByteArrayInputStream(stamped),
                stamped.length
        );
    }

    private StoredFile storeGeneratedPdf(byte[] pdfBytes, String templateCode) {
        String filename = sanitizeFilename(templateCode) + "-generated.pdf";
        return storageProvider.store(
                StorageCategory.GENERATED,
                filename,
                PDF_MIME,
                new ByteArrayInputStream(pdfBytes),
                pdfBytes.length
        );
    }

    private GeneratedDocument persist(
            DocuForgePrincipal principal,
            Template template,
            TemplateVersion version,
            String title,
            Map<String, Object> data,
            String storageKey,
            String checksum,
            DocumentStatus status,
            UUID rootDocumentId,
            UUID parentDocumentId,
            int documentVersionNumber
    ) {
        Company company = companyRepository.getReferenceById(principal.getCompanyId());
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                GeneratedDocument document = new GeneratedDocument();
                document.setCompany(company);
                document.setTemplate(template);
                document.setTemplateVersion(version);
                document.setRootDocumentId(rootDocumentId);
                document.setParentDocumentId(parentDocumentId);
                document.setDocumentVersionNumber(documentVersionNumber);
                document.setReference(documentReferenceService.allocate(principal.getCompanyId()));
                document.setTitle(title);
                document.setStatus(status);
                document.setDataSnapshot(toJson(data));
                document.setDocxStorageKey(storageKey);
                document.setChecksum(checksum);
                document.setCreatedBy(principal.getUserId());
                return generatedDocumentRepository.saveAndFlush(document);
            } catch (DataIntegrityViolationException ex) {
                if (attempt == 4) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Impossible d'allouer une reference document unique.",
                            ex
                    );
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible d'allouer une reference document unique.");
    }

    private GeneratedDocument requireDocument(UUID companyId, UUID documentId) {
        return generatedDocumentRepository.findByIdAndCompanyId(documentId, companyId)
                .orElseThrow(() -> notFound("Document introuvable"));
    }

    private static String resolveTitle(DocumentGenerateRequest request, Template template) {
        if (request.title() != null && !request.title().isBlank()) {
            return request.title().trim();
        }
        return template.getName();
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donnees JSON invalides.", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDataSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private void writeAudit(DocuForgePrincipal principal, String action, UUID entityId) {
        auditService.recordSuccess(principal, action, "DOCUMENT", entityId);
    }

    private GeneratedDocumentResponse toResponse(GeneratedDocument document, String createdByName) {
        return new GeneratedDocumentResponse(
                document.getId(),
                document.getReference(),
                document.getTitle(),
                document.getStatus(),
                document.getTemplate().getId(),
                document.getTemplate().getCode(),
                document.getTemplate().getName(),
                document.getTemplateVersion().getId(),
                document.getTemplateVersion().getVersionNumber(),
                document.getDocumentVersionNumber(),
                document.getRootDocumentId() != null ? document.getRootDocumentId() : document.getId(),
                document.getParentDocumentId(),
                document.getDocxStorageKey(),
                document.getPdfStorageKey(),
                document.getChecksum(),
                document.getCreatedBy(),
                createdByName,
                document.getCreatedAt(),
                parseDataSnapshot(document.getDataSnapshot())
        );
    }

    private Map<UUID, String> resolveCreatorNames(List<GeneratedDocument> documents) {
        Set<UUID> ids = documents.stream()
                .map(GeneratedDocument::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (UserAccount user : userAccountRepository.findAllById(ids)) {
            names.put(user.getId(), formatUserName(user));
        }
        return names;
    }

    private String resolveCreatorName(UUID createdBy) {
        if (createdBy == null) {
            return null;
        }
        return userAccountRepository.findById(createdBy)
                .map(DocumentGenerationService::formatUserName)
                .orElse(null);
    }

    private static String formatUserName(UserAccount user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static void deleteRecursivelyQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private static String sanitizeFilename(String code) {
        if (code == null || code.isBlank()) {
            return "document";
        }
        return code.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
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

    private record GenerationContext(
            Template template,
            TemplateVersion version,
            List<TemplateVariable> variables
    ) {
    }

    public record DocumentDownload(String filename, String contentType, InputStreamResource body) {
    }
}
