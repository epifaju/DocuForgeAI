package ai.docuforge.batch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.config.BatchProperties;
import ai.docuforge.domain.batch.BatchItemRepository;
import ai.docuforge.domain.batch.BatchJob;
import ai.docuforge.domain.batch.BatchJobRepository;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.storage.StorageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock private BatchJobRepository batchJobRepository;
    @Mock private BatchItemRepository batchItemRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateVersionRepository templateVersionRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private StorageProvider storageProvider;
    @Mock private BatchProcessingService batchProcessingService;
    @Mock private AuditService auditService;

    private BatchService service;
    private DocuForgePrincipal principal;
    private final UUID companyId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BatchService(
                batchJobRepository,
                batchItemRepository,
                templateRepository,
                templateVersionRepository,
                companyRepository,
                storageProvider,
                new BatchProperties(50, true),
                batchProcessingService,
                auditService,
                new ObjectMapper()
        );
        principal = new DocuForgePrincipal(
                UUID.randomUUID(), companyId, "batch-co", "admin@batch-co.test", "h", true, Set.of("ADMIN")
        );
    }

    @Test
    void rejectsEmptyAndNonCsvFiles() {
        assertThatThrownBy(() -> service.create(
                principal, templateId, null, null, new MockMultipartFile("file", "x.csv", "text/csv", new byte[0])
        )).isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> service.create(
                principal,
                templateId,
                null,
                null,
                new MockMultipartFile("file", "x.txt", "text/plain", "a".getBytes())
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(".csv");
        verify(storageProvider, never()).store(any(), any(), any(), any(), anyLong());
    }

    @Test
    void rejectsInactiveTemplateAndInvalidMapping() {
        Template template = new Template();
        template.setId(templateId);
        template.setStatus(TemplateStatus.DRAFT);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.create(
                principal,
                templateId,
                null,
                null,
                new MockMultipartFile("file", "rows.csv", "text/csv", "a,b\n1,2".getBytes())
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        template.setStatus(TemplateStatus.ACTIVE);
        TemplateVersion version = new TemplateVersion();
        version.setId(UUID.randomUUID());
        version.setTemplate(template);
        template.setCurrentVersion(version);

        assertThatThrownBy(() -> service.create(
                principal,
                templateId,
                null,
                "{not-json",
                new MockMultipartFile("file", "rows.csv", "text/csv", "a,b\n1,2".getBytes())
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Mapping");
    }

    @Test
    void rejectsMissingCurrentVersion() {
        Template template = new Template();
        template.setId(templateId);
        template.setStatus(TemplateStatus.ACTIVE);
        template.setCurrentVersion(null);
        when(templateRepository.findByIdAndCompanyIdWithCurrentVersion(templateId, companyId))
                .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.create(
                principal,
                templateId,
                null,
                null,
                new MockMultipartFile("file", "rows.csv", "text/csv", "a,b\n1,2".getBytes())
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void downloadZipAndErrorsRequireStorageKeys() {
        UUID jobId = UUID.randomUUID();
        BatchJob job = new BatchJob();
        job.setId(jobId);
        when(batchJobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.downloadZip(principal, jobId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> service.downloadErrors(principal, jobId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
