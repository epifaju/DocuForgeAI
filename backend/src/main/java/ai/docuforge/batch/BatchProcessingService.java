package ai.docuforge.batch;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ErrorResponse.FieldErrorDetail;
import ai.docuforge.common.i18n.ErrorMessages;
import ai.docuforge.domain.batch.BatchItem;
import ai.docuforge.domain.batch.BatchItemRepository;
import ai.docuforge.domain.batch.BatchItemStatus;
import ai.docuforge.domain.batch.BatchJob;
import ai.docuforge.domain.batch.BatchJobRepository;
import ai.docuforge.domain.batch.BatchJobStatus;
import ai.docuforge.domain.document.GeneratedDocument;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.document.DocumentGenerationService;
import ai.docuforge.document.dto.GeneratedDocumentResponse;
import ai.docuforge.form.FormDataValidator;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BatchProcessingService {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessingService.class);

    private final BatchJobRepository batchJobRepository;
    private final BatchItemRepository batchItemRepository;
    private final TemplateVariableRepository templateVariableRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final StorageProvider storageProvider;
    private final CsvBatchParser csvBatchParser;
    private final FormDataValidator formDataValidator;
    private final DocumentGenerationService documentGenerationService;
    private final AuditService auditService;
    private final ErrorMessages errorMessages;
    private final ObjectMapper objectMapper;
    private final ai.docuforge.config.BatchProperties batchProperties;
    private final TransactionTemplate transactionTemplate;

    public BatchProcessingService(
            BatchJobRepository batchJobRepository,
            BatchItemRepository batchItemRepository,
            TemplateVariableRepository templateVariableRepository,
            GeneratedDocumentRepository generatedDocumentRepository,
            StorageProvider storageProvider,
            CsvBatchParser csvBatchParser,
            FormDataValidator formDataValidator,
            DocumentGenerationService documentGenerationService,
            AuditService auditService,
            ErrorMessages errorMessages,
            ObjectMapper objectMapper,
            ai.docuforge.config.BatchProperties batchProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.batchJobRepository = batchJobRepository;
        this.batchItemRepository = batchItemRepository;
        this.templateVariableRepository = templateVariableRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.storageProvider = storageProvider;
        this.csvBatchParser = csvBatchParser;
        this.formDataValidator = formDataValidator;
        this.documentGenerationService = documentGenerationService;
        this.auditService = auditService;
        this.errorMessages = errorMessages;
        this.objectMapper = objectMapper;
        this.batchProperties = batchProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("batchTaskExecutor")
    public void processAsync(
            UUID jobId,
            UUID userId,
            UUID companyId,
            String companyIdentifier,
            String email,
            Set<String> roles
    ) {
        processNow(jobId, userId, companyId, companyIdentifier, email, roles);
    }

    public void processNow(
            UUID jobId,
            UUID userId,
            UUID companyId,
            String companyIdentifier,
            String email,
            Set<String> roles
    ) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    process(jobId, userId, companyId, companyIdentifier, email, roles)
            );
        } catch (Exception ex) {
            log.error("Batch {} failed: {}", jobId, ex.getMessage(), ex);
            transactionTemplate.executeWithoutResult(status -> markFailed(jobId, ex.getMessage()));
        }
    }

    private void process(
            UUID jobId,
            UUID userId,
            UUID companyId,
            String companyIdentifier,
            String email,
            Set<String> roles
    ) {
        BatchJob job = batchJobRepository.findById(jobId).orElseThrow();
        job.setStatus(BatchJobStatus.VALIDATING);
        job.setStartedAt(Instant.now());
        job.setLockedAt(Instant.now());
        job.setLockedBy("batch-" + Thread.currentThread().getName());
        batchJobRepository.saveAndFlush(job);

        byte[] csvBytes;
        try (InputStream in = storageProvider.read(job.getCsvStorageKey())) {
            csvBytes = in.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("CSV introuvable pour le batch.", ex);
        }

        List<CsvBatchParser.CsvRow> rows = csvBatchParser.parse(csvBytes, Math.max(1, batchProperties.maxRows()));
        Map<String, String> mapping = readMapping(job.getColumnMapping());

        job.setTotalItems(rows.size());
        job.setStatus(BatchJobStatus.PROCESSING);
        batchJobRepository.saveAndFlush(job);

        List<TemplateVariable> variables = templateVariableRepository
                .findByTemplateVersionIdOrderByDisplayOrderAsc(job.getTemplateVersion().getId());
        DocuForgePrincipal principal = new DocuForgePrincipal(
                userId, companyId, companyIdentifier, email, "", true, roles
        );
        UUID templateId = job.getTemplate() != null
                ? job.getTemplate().getId()
                : job.getTemplateVersion().getTemplate().getId();

        int success = 0;
        int failed = 0;
        for (CsvBatchParser.CsvRow row : rows) {
            try {
                Map<String, Object> data = CsvBatchParser.applyMapping(row.columns(), mapping);
                List<FieldErrorDetail> errors = formDataValidator.validateCollecting(variables, data);
                if (!errors.isEmpty()) {
                    String message = errors.stream()
                            .map(e -> e.field() + ": " + errorMessages.localize(e.message()))
                            .reduce((a, b) -> a + "; " + b)
                            .orElse(errorMessages.localize("error.form.invalid"));
                    saveFailedItem(job, row.rowNumber(), message);
                    failed++;
                } else {
                    String templateName = job.getTemplate() != null
                            ? job.getTemplate().getName()
                            : "Document";
                    String title = templateName + " #" + row.rowNumber();
                    GeneratedDocumentResponse doc = documentGenerationService.generateMapped(
                            principal,
                            templateId,
                            job.getTemplateVersion().getId(),
                            data,
                            title
                    );
                    saveSuccessItem(job, row.rowNumber(), doc.id());
                    success++;
                }
            } catch (ResponseStatusException ex) {
                saveFailedItem(job, row.rowNumber(), ex.getReason() == null ? ex.getMessage() : ex.getReason());
                failed++;
            } catch (Exception ex) {
                saveFailedItem(job, row.rowNumber(), ex.getMessage() == null ? "Erreur inattendue" : ex.getMessage());
                failed++;
            }
            job.setProcessedItems(success + failed);
            job.setSuccessfulItems(success);
            job.setFailedItems(failed);
            batchJobRepository.saveAndFlush(job);
        }

        storeArtifacts(job);
        if (failed == 0) {
            job.setStatus(BatchJobStatus.COMPLETED);
        } else if (success == 0) {
            job.setStatus(BatchJobStatus.FAILED);
        } else {
            job.setStatus(BatchJobStatus.PARTIALLY_FAILED);
        }
        job.setCompletedAt(Instant.now());
        job.setLockedAt(null);
        job.setLockedBy(null);
        batchJobRepository.saveAndFlush(job);

        auditService.recordSystem(
                companyId,
                userId,
                AuditActions.BATCH_COMPLETED,
                "BATCH",
                jobId.toString(),
                "SUCCESS",
                Map.of(
                        "status", job.getStatus().name(),
                        "successfulItems", job.getSuccessfulItems(),
                        "failedItems", job.getFailedItems()
                )
        );
    }

    private void storeArtifacts(BatchJob job) {
        List<BatchItem> successes = batchItemRepository
                .findByBatchJobIdAndStatusOrderByRowNumberAsc(job.getId(), BatchItemStatus.SUCCESS);
        if (!successes.isEmpty()) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(bos)) {
                for (BatchItem item : successes) {
                    GeneratedDocument doc = generatedDocumentRepository.findById(item.getGeneratedDocumentId())
                            .orElse(null);
                    if (doc == null || doc.getDocxStorageKey() == null) {
                        continue;
                    }
                    try (InputStream in = storageProvider.read(doc.getDocxStorageKey())) {
                        zos.putNextEntry(new ZipEntry(doc.getReference() + ".docx"));
                        in.transferTo(zos);
                        zos.closeEntry();
                    }
                }
                zos.finish();
                byte[] zipBytes = bos.toByteArray();
                StoredFile zip = storageProvider.store(
                        StorageCategory.GENERATED,
                        "batch-" + job.getId() + ".zip",
                        "application/zip",
                        new ByteArrayInputStream(zipBytes),
                        zipBytes.length
                );
                job.setZipStorageKey(zip.storageKey());
            } catch (Exception ex) {
                log.warn("ZIP batch {} impossible: {}", job.getId(), ex.getMessage());
            }
        }

        List<BatchItem> failures = batchItemRepository
                .findByBatchJobIdAndStatusOrderByRowNumberAsc(job.getId(), BatchItemStatus.FAILED);
        if (!failures.isEmpty()) {
            StringBuilder csv = new StringBuilder("row_number,message\n");
            for (BatchItem item : failures) {
                csv.append(item.getRowNumber())
                        .append(',')
                        .append(escapeCsv(item.getErrorMessage()))
                        .append('\n');
            }
            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            StoredFile report = storageProvider.store(
                    StorageCategory.GENERATED,
                    "batch-" + job.getId() + "-errors.csv",
                    "text/csv",
                    new ByteArrayInputStream(bytes),
                    bytes.length
            );
            job.setErrorReportStorageKey(report.storageKey());
        }
    }

    private void saveFailedItem(BatchJob job, int rowNumber, String message) {
        BatchItem item = new BatchItem();
        item.setBatchJob(job);
        item.setRowNumber(rowNumber);
        item.setStatus(BatchItemStatus.FAILED);
        item.setErrorMessage(truncate(message, 2000));
        batchItemRepository.saveAndFlush(item);
    }

    private void saveSuccessItem(BatchJob job, int rowNumber, UUID documentId) {
        BatchItem item = new BatchItem();
        item.setBatchJob(job);
        item.setRowNumber(rowNumber);
        item.setStatus(BatchItemStatus.SUCCESS);
        item.setGeneratedDocumentId(documentId);
        batchItemRepository.saveAndFlush(item);
    }

    private void markFailed(UUID jobId, String message) {
        batchJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(BatchJobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setLockedAt(null);
            job.setLockedBy(null);
            if (job.getProcessedItems() == 0 && message != null) {
                saveFailedItem(job, 0, truncate(message, 2000));
                job.setFailedItems(Math.max(job.getFailedItems(), 1));
                job.setProcessedItems(Math.max(job.getProcessedItems(), 1));
            }
            batchJobRepository.saveAndFlush(job);
        });
    }

    private Map<String, String> readMapping(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
