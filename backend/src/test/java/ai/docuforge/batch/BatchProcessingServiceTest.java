package ai.docuforge.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.audit.AuditService;
import ai.docuforge.common.api.ErrorResponse.FieldErrorDetail;
import ai.docuforge.common.i18n.ErrorMessages;
import ai.docuforge.config.BatchProperties;
import ai.docuforge.domain.batch.BatchItem;
import ai.docuforge.domain.batch.BatchItemRepository;
import ai.docuforge.domain.batch.BatchItemStatus;
import ai.docuforge.domain.batch.BatchJob;
import ai.docuforge.domain.batch.BatchJobRepository;
import ai.docuforge.domain.batch.BatchJobStatus;
import ai.docuforge.domain.document.DocumentStatus;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.document.DocumentGenerationService;
import ai.docuforge.document.dto.GeneratedDocumentResponse;
import ai.docuforge.form.FormDataValidator;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BatchProcessingServiceTest {

    @Mock private BatchJobRepository batchJobRepository;
    @Mock private BatchItemRepository batchItemRepository;
    @Mock private TemplateVariableRepository templateVariableRepository;
    @Mock private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock private StorageProvider storageProvider;
    @Mock private CsvBatchParser csvBatchParser;
    @Mock private FormDataValidator formDataValidator;
    @Mock private DocumentGenerationService documentGenerationService;
    @Mock private AuditService auditService;
    @Mock private ErrorMessages errorMessages;

    private BatchProcessingService service;
    private final UUID jobId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BatchProcessingService(
                batchJobRepository,
                batchItemRepository,
                templateVariableRepository,
                generatedDocumentRepository,
                storageProvider,
                csvBatchParser,
                formDataValidator,
                documentGenerationService,
                auditService,
                errorMessages,
                new ObjectMapper(),
                new BatchProperties(100, true),
                new NoOpTransactionManager()
        );
    }

    @Test
    void processNow_marksJobFailedWhenCsvMissing() throws Exception {
        BatchJob job = baseJob();
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(storageProvider.read(anyString())).thenThrow(new IllegalStateException("gone"));

        service.processNow(jobId, userId, companyId, "demo", "a@b.c", Set.of("ADMIN"));

        assertThat(job.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getLockedAt()).isNull();
        verify(batchItemRepository).saveAndFlush(any(BatchItem.class));
    }

    @Test
    void processNow_completesWhenAllRowsSucceed() throws Exception {
        BatchJob job = baseJob();
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(storageProvider.read("csv-key")).thenReturn(new ByteArrayInputStream("h\nv\n".getBytes()));
        when(csvBatchParser.parse(any(), anyInt()))
                .thenReturn(List.of(new CsvBatchParser.CsvRow(1, Map.of("name", "Ada"))));
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of());
        when(formDataValidator.validateCollecting(any(), any())).thenReturn(List.of());
        UUID docId = UUID.randomUUID();
        when(documentGenerationService.generateMapped(any(), eq(templateId), eq(versionId), any(), any()))
                .thenReturn(sampleDoc(docId));
        when(batchItemRepository.findByBatchJobIdAndStatusOrderByRowNumberAsc(jobId, BatchItemStatus.SUCCESS))
                .thenReturn(List.of());
        when(batchItemRepository.findByBatchJobIdAndStatusOrderByRowNumberAsc(jobId, BatchItemStatus.FAILED))
                .thenReturn(List.of());

        service.processNow(jobId, userId, companyId, "demo", "a@b.c", Set.of("ADMIN"));

        assertThat(job.getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(job.getSuccessfulItems()).isEqualTo(1);
        assertThat(job.getFailedItems()).isEqualTo(0);
        verify(auditService).recordSystem(
                eq(companyId), eq(userId), any(), eq("BATCH"), eq(jobId.toString()), eq("SUCCESS"), any()
        );
    }

    @Test
    void processNow_failsEntirelyWhenValidationFailsForAllRows() throws Exception {
        BatchJob job = baseJob();
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(storageProvider.read("csv-key")).thenReturn(new ByteArrayInputStream("x".getBytes()));
        when(csvBatchParser.parse(any(), anyInt()))
                .thenReturn(List.of(new CsvBatchParser.CsvRow(1, Map.of("name", ""))));
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of());
        when(formDataValidator.validateCollecting(any(), any()))
                .thenReturn(List.of(new FieldErrorDetail("name", "required")));
        when(errorMessages.localize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(batchItemRepository.findByBatchJobIdAndStatusOrderByRowNumberAsc(jobId, BatchItemStatus.SUCCESS))
                .thenReturn(List.of());
        when(batchItemRepository.findByBatchJobIdAndStatusOrderByRowNumberAsc(jobId, BatchItemStatus.FAILED))
                .thenAnswer(inv -> {
                    BatchItem item = new BatchItem();
                    item.setRowNumber(1);
                    item.setStatus(BatchItemStatus.FAILED);
                    item.setErrorMessage("name: required");
                    return List.of(item);
                });
        when(storageProvider.store(eq(StorageCategory.GENERATED), anyString(), eq("text/csv"), any(), anyLong()))
                .thenReturn(new StoredFile(
                        "err-key", "batch-errors.csv", "text/csv", "csv", 10, StorageCategory.GENERATED
                ));

        service.processNow(jobId, userId, companyId, "demo", "a@b.c", Set.of("USER"));

        assertThat(job.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(job.getFailedItems()).isEqualTo(1);
        assertThat(job.getErrorReportStorageKey()).isEqualTo("err-key");
        verify(documentGenerationService, never()).generateMapped(any(), any(), any(), any(), any());
    }

    @Test
    void processNow_marksPartiallyFailedWhenMixedResults() throws Exception {
        BatchJob job = baseJob();
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(storageProvider.read("csv-key")).thenReturn(new ByteArrayInputStream("x".getBytes()));
        when(csvBatchParser.parse(any(), anyInt())).thenReturn(List.of(
                new CsvBatchParser.CsvRow(1, Map.of("name", "ok")),
                new CsvBatchParser.CsvRow(2, Map.of("name", "bad"))
        ));
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of());
        when(formDataValidator.validateCollecting(any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = inv.getArgument(1);
            if ("bad".equals(String.valueOf(data.get("name")))) {
                return List.of(new FieldErrorDetail("name", "invalid"));
            }
            return List.of();
        });
        when(errorMessages.localize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(documentGenerationService.generateMapped(any(), eq(templateId), eq(versionId), any(), any()))
                .thenReturn(sampleDoc(UUID.randomUUID()));
        when(batchItemRepository.findByBatchJobIdAndStatusOrderByRowNumberAsc(jobId, BatchItemStatus.SUCCESS))
                .thenReturn(List.of());
        when(batchItemRepository.findByBatchJobIdAndStatusOrderByRowNumberAsc(jobId, BatchItemStatus.FAILED))
                .thenReturn(List.of());

        service.processNow(jobId, userId, companyId, "demo", "a@b.c", Set.of("ADMIN"));

        assertThat(job.getStatus()).isEqualTo(BatchJobStatus.PARTIALLY_FAILED);
        assertThat(job.getSuccessfulItems()).isEqualTo(1);
        assertThat(job.getFailedItems()).isEqualTo(1);
    }

    @Test
    void processNow_recordsGenerationHttpErrorsAsFailedItems() throws Exception {
        BatchJob job = baseJob();
        when(batchJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(storageProvider.read("csv-key")).thenReturn(new ByteArrayInputStream("x".getBytes()));
        when(csvBatchParser.parse(any(), anyInt()))
                .thenReturn(List.of(new CsvBatchParser.CsvRow(1, Map.of("name", "Ada"))));
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of());
        when(formDataValidator.validateCollecting(any(), any())).thenReturn(List.of());
        when(documentGenerationService.generateMapped(any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "bad data"));
        when(batchItemRepository.findByBatchJobIdAndStatusOrderByRowNumberAsc(any(), any()))
                .thenReturn(List.of());

        service.processNow(jobId, userId, companyId, "demo", "a@b.c", Set.of("ADMIN"));

        ArgumentCaptor<BatchItem> itemCaptor = ArgumentCaptor.forClass(BatchItem.class);
        verify(batchItemRepository).saveAndFlush(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getStatus()).isEqualTo(BatchItemStatus.FAILED);
        assertThat(itemCaptor.getValue().getErrorMessage()).isEqualTo("bad data");
        assertThat(job.getStatus()).isEqualTo(BatchJobStatus.FAILED);
    }

    private GeneratedDocumentResponse sampleDoc(UUID docId) {
        return new GeneratedDocumentResponse(
                docId,
                "DOC-1",
                "title",
                DocumentStatus.COMPLETED,
                templateId,
                "CODE",
                "Lettre",
                versionId,
                1,
                1,
                docId,
                null,
                "docx-key",
                null,
                "checksum",
                userId,
                "Admin",
                Instant.now(),
                Map.of()
        );
    }

    private BatchJob baseJob() {
        Template template = new Template();
        template.setId(templateId);
        template.setName("Lettre");
        TemplateVersion version = new TemplateVersion();
        version.setId(versionId);
        version.setTemplate(template);

        BatchJob job = new BatchJob();
        job.setId(jobId);
        job.setStatus(BatchJobStatus.CREATED);
        job.setCsvStorageKey("csv-key");
        job.setColumnMapping("{}");
        job.setTemplate(template);
        job.setTemplateVersion(version);
        job.setTotalItems(0);
        job.setProcessedItems(0);
        job.setSuccessfulItems(0);
        job.setFailedItems(0);
        return job;
    }

    /** Minimal TX manager so TransactionTemplate.executeWithoutResult runs the callback. */
    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(TransactionDefinition definition)
                throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) throws TransactionException {
            // no-op
        }

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) throws TransactionException {
            // no-op
        }
    }
}
