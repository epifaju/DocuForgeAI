package ai.docuforge.batch;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.batch.dto.BatchErrorItem;
import ai.docuforge.batch.dto.BatchJobResponse;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.config.BatchProperties;
import ai.docuforge.domain.batch.BatchItem;
import ai.docuforge.domain.batch.BatchItemRepository;
import ai.docuforge.domain.batch.BatchItemStatus;
import ai.docuforge.domain.batch.BatchJob;
import ai.docuforge.domain.batch.BatchJobRepository;
import ai.docuforge.domain.batch.BatchJobStatus;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StoragePathGuard;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BatchService {

    private final BatchJobRepository batchJobRepository;
    private final BatchItemRepository batchItemRepository;
    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final CompanyRepository companyRepository;
    private final StorageProvider storageProvider;
    private final BatchProperties batchProperties;
    private final BatchProcessingService batchProcessingService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public BatchService(
            BatchJobRepository batchJobRepository,
            BatchItemRepository batchItemRepository,
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            CompanyRepository companyRepository,
            StorageProvider storageProvider,
            BatchProperties batchProperties,
            BatchProcessingService batchProcessingService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.batchJobRepository = batchJobRepository;
        this.batchItemRepository = batchItemRepository;
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.companyRepository = companyRepository;
        this.storageProvider = storageProvider;
        this.batchProperties = batchProperties;
        this.batchProcessingService = batchProcessingService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BatchJobResponse create(
            DocuForgePrincipal principal,
            UUID templateId,
            UUID templateVersionId,
            String mappingJson,
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Fichier CSV requis.");
        }
        String filename = file.getOriginalFilename() == null ? "batch.csv" : file.getOriginalFilename();
        StoragePathGuard.sanitizeOriginalFilename(filename);
        if (!filename.toLowerCase().endsWith(".csv")) {
            throw badRequest("Seuls les fichiers .csv sont acceptes.");
        }

        Template template = templateRepository
                .findByIdAndCompanyIdWithCurrentVersion(templateId, principal.getCompanyId())
                .orElseThrow(() -> notFound("Template introuvable"));
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw conflict("Seuls les templates ACTIVE acceptent un batch.");
        }

        TemplateVersion version;
        if (templateVersionId != null) {
            version = templateVersionRepository
                    .findByIdAndCompanyId(templateVersionId, principal.getCompanyId())
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

        Map<String, String> mapping = parseMapping(mappingJson);
        byte[] csvBytes;
        try {
            csvBytes = file.getBytes();
        } catch (Exception ex) {
            throw badRequest("Lecture du CSV impossible.");
        }

        StoredFile storedCsv = storageProvider.store(
                StorageCategory.TEMPORARY,
                filename,
                "text/csv",
                new ByteArrayInputStream(csvBytes),
                csvBytes.length
        );

        BatchJob job = new BatchJob();
        job.setCompany(companyRepository.getReferenceById(principal.getCompanyId()));
        job.setTemplate(template);
        job.setTemplateVersion(version);
        job.setStatus(BatchJobStatus.CREATED);
        job.setTotalItems(0);
        job.setProcessedItems(0);
        job.setSuccessfulItems(0);
        job.setFailedItems(0);
        job.setCsvStorageKey(storedCsv.storageKey());
        job.setColumnMapping(writeJson(mapping));
        job.setCreatedBy(principal.getUserId());
        job = batchJobRepository.saveAndFlush(job);

        writeAudit(principal, AuditActions.BATCH_STARTED, job.getId());
        if (batchProperties.sync()) {
            batchProcessingService.processNow(
                    job.getId(),
                    principal.getUserId(),
                    principal.getCompanyId(),
                    principal.getCompanyIdentifier(),
                    principal.getUsername(),
                    principal.getRoles()
            );
        } else {
            batchProcessingService.processAsync(
                    job.getId(),
                    principal.getUserId(),
                    principal.getCompanyId(),
                    principal.getCompanyIdentifier(),
                    principal.getUsername(),
                    principal.getRoles()
            );
        }
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchJobResponse> list(DocuForgePrincipal principal, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<BatchJob> result = batchJobRepository.findByCompanyIdOrderByCreatedAtDesc(
                principal.getCompanyId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public BatchJobResponse get(DocuForgePrincipal principal, UUID id) {
        return toResponse(requireJob(principal.getCompanyId(), id));
    }

    @Transactional(readOnly = true)
    public List<BatchErrorItem> errors(DocuForgePrincipal principal, UUID id) {
        requireJob(principal.getCompanyId(), id);
        return batchItemRepository
                .findByBatchJobIdAndStatusOrderByRowNumberAsc(id, BatchItemStatus.FAILED)
                .stream()
                .map(item -> new BatchErrorItem(item.getRowNumber(), item.getErrorMessage()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Download downloadZip(DocuForgePrincipal principal, UUID id) {
        BatchJob job = requireJob(principal.getCompanyId(), id);
        if (job.getZipStorageKey() == null || job.getZipStorageKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Archive ZIP indisponible.");
        }
        InputStream in = storageProvider.read(job.getZipStorageKey());
        return new Download("batch-" + job.getId() + ".zip", "application/zip", new InputStreamResource(in));
    }

    @Transactional(readOnly = true)
    public Download downloadErrors(DocuForgePrincipal principal, UUID id) {
        BatchJob job = requireJob(principal.getCompanyId(), id);
        if (job.getErrorReportStorageKey() == null || job.getErrorReportStorageKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rapport d'erreurs indisponible.");
        }
        InputStream in = storageProvider.read(job.getErrorReportStorageKey());
        return new Download(
                "batch-" + job.getId() + "-errors.csv",
                "text/csv",
                new InputStreamResource(in)
        );
    }

    public int maxRows() {
        return Math.max(1, batchProperties.maxRows());
    }

    private Map<String, String> parseMapping(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(mappingJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw badRequest("Mapping JSON invalide.");
        }
    }

    private String writeJson(Map<String, String> mapping) {
        try {
            return objectMapper.writeValueAsString(mapping == null ? Map.of() : mapping);
        } catch (Exception ex) {
            throw badRequest("Mapping JSON invalide.");
        }
    }

    private BatchJob requireJob(UUID companyId, UUID id) {
        return batchJobRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> notFound("Batch introuvable"));
    }

    private BatchJobResponse toResponse(BatchJob job) {
        UUID templateId = job.getTemplate() != null
                ? job.getTemplate().getId()
                : job.getTemplateVersion().getTemplate().getId();
        return new BatchJobResponse(
                job.getId(),
                job.getStatus(),
                templateId,
                job.getTemplateVersion().getId(),
                job.getTotalItems(),
                job.getProcessedItems(),
                job.getSuccessfulItems(),
                job.getFailedItems(),
                job.getZipStorageKey() != null && !job.getZipStorageKey().isBlank(),
                job.getErrorReportStorageKey() != null && !job.getErrorReportStorageKey().isBlank(),
                job.getCreatedBy(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }

    private void writeAudit(DocuForgePrincipal principal, String action, UUID entityId) {
        auditService.recordSuccess(principal, action, "BATCH", entityId);
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

    public record Download(String filename, String contentType, InputStreamResource body) {
    }
}
