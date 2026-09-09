package ai.docuforge.businesspack.importjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.compatibility.PackCompatibilityService;
import ai.docuforge.businesspack.dto.PackImportJobResponse;
import ai.docuforge.businesspack.validation.PackValidationReport;
import ai.docuforge.businesspack.validation.PackValidationService;
import ai.docuforge.config.PackProperties;
import ai.docuforge.domain.businesspack.PackImportJob;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.businesspack.PackImportJobStatus;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
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
class PackImportServiceTest {

    @Mock private PackImportJobRepository jobRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private BusinessPackRepository packRepository;
    @Mock private StorageProvider storageProvider;
    @Mock private PackValidationService validationService;
    @Mock private PackCompatibilityService compatibilityService;
    @Mock private AuditService auditService;

    private PackImportService service;
    private PackProperties enabledProps;
    private DocuForgePrincipal principal;
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        enabledProps = new PackProperties(
                true, 10, 50, 100, 20, 10, 50, 24, "0.1.0", Set.of("json", "docx", "txt", "png", "md", "csv")
        );
        service = newService(enabledProps);
        principal = new DocuForgePrincipal(
                UUID.randomUUID(), companyId, "pack-co", "admin@pack-co.test", "h", true, Set.of("ADMIN")
        );
    }

    @Test
    void rejectsWhenFeatureDisabled() {
        PackImportService disabled = newService(new PackProperties(
                false, 10, 50, 100, 20, 10, 50, 24, "0.1.0", Set.of("zip")
        ));
        assertThatThrownBy(() -> disabled.upload(principal, zipFile(new byte[] {0x50, 0x4B, 0x03, 0x04})))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void uploadRejectsEmptyNonZipOversizedAndBadMagic() {
        assertThatThrownBy(() -> service.upload(principal, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("error.pack.import_file_required");

        assertThatThrownBy(() -> service.upload(
                        principal, new MockMultipartFile("file", "x.csv", "text/csv", "a".getBytes())))
                .hasMessageContaining("error.pack.import_zip_required");

        byte[] huge = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00};
        MockMultipartFile oversized = new MockMultipartFile("file", "p.zip", "application/zip", huge) {
            @Override
            public long getSize() {
                return enabledProps.maxUploadBytes() + 1;
            }
        };
        assertThatThrownBy(() -> service.upload(principal, oversized))
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        assertThatThrownBy(() -> service.upload(
                        principal, new MockMultipartFile("file", "p.zip", "application/zip", "nope".getBytes())))
                .hasMessageContaining("error.pack.import_zip_required");
    }

    @Test
    void uploadStoresJobAndAudits() throws Exception {
        byte[] zip = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};
        StoredFile stored = new StoredFile(
                "packimports/a.zip", "demo.zip", "application/zip", "zip", zip.length, StorageCategory.PACK_IMPORTS
        );
        when(storageProvider.store(eq(StorageCategory.PACK_IMPORTS), anyString(), anyString(), any(), anyLong()))
                .thenReturn(stored);
        when(companyRepository.getReferenceById(companyId)).thenReturn(new Company());
        when(jobRepository.save(any(PackImportJob.class))).thenAnswer(inv -> {
            PackImportJob job = inv.getArgument(0);
            job.setId(jobId);
            job.setCreatedAt(Instant.now());
            return job;
        });

        PackImportJobResponse response = service.upload(principal, zipFile(zip));

        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(PackImportJobStatus.UPLOADED);
        assertThat(response.originalFilename()).isEqualTo("demo.zip");
        verify(auditService).record(
                eq(principal), eq(AuditActions.PACK_UPLOADED), eq("PACK_IMPORT_JOB"), eq(jobId.toString()),
                eq("SUCCESS"), any()
        );
    }

    @Test
    void getNotFoundAndParsesBlankReportAsNull() {
        when(jobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(principal, jobId))
                .hasMessageContaining("error.pack.import_not_found");

        PackImportJob job = baseJob(PackImportJobStatus.UPLOADED);
        job.setValidationReport("   ");
        when(jobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.of(job));
        assertThat(service.get(principal, jobId).validationReport()).isNull();
    }

    @Test
    void validateRejectsExpiredAndAlreadyProcessed() {
        PackImportJob expired = baseJob(PackImportJobStatus.EXPIRED);
        when(jobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.validate(principal, jobId))
                .hasMessageContaining("error.pack.import_expired");

        PackImportJob installed = baseJob(PackImportJobStatus.INSTALLED);
        when(jobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.of(installed));
        assertThatThrownBy(() -> service.validate(principal, jobId))
                .hasMessageContaining("error.pack.import_already_processed");
    }

    @Test
    void validateMarksFailedWhenStagingMissing() {
        PackImportJob job = baseJob(PackImportJobStatus.UPLOADED);
        when(jobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.of(job));
        when(storageProvider.exists("packimports/a.zip")).thenReturn(false);
        when(jobRepository.save(any(PackImportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.validate(principal, jobId))
                .hasMessageContaining("error.pack.file_missing");
        assertThat(job.getStatus()).isEqualTo(PackImportJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("PACK_FILE_MISSING");
    }

    @Test
    void validateSuccessAndInvalidPaths() throws Exception {
        PackImportJob job = baseJob(PackImportJobStatus.UPLOADED);
        when(jobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.of(job));
        when(storageProvider.exists("packimports/a.zip")).thenReturn(true);
        when(storageProvider.read("packimports/a.zip")).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        PackValidationReport valid = PackValidationReport.of(
                new PackValidationReport.PackIdentity("com.demo", "Demo", "1.0.0"),
                List.of(),
                1,
                0
        );
        when(validationService.validate(any())).thenReturn(valid);
        when(packRepository.findByCompanyIdAndPackKeyWithCurrentVersion(companyId, "com.demo"))
                .thenReturn(Optional.empty());

        PackImportJobResponse ok = service.validate(principal, jobId);
        assertThat(ok.status()).isEqualTo(PackImportJobStatus.VALID);
        verify(auditService).record(
                eq(principal), eq(AuditActions.PACK_VALIDATED), eq("PACK_IMPORT_JOB"), eq(jobId.toString()),
                eq("SUCCESS"), any()
        );

        PackImportJob job2 = baseJob(PackImportJobStatus.UPLOADED);
        when(jobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.of(job2));
        PackValidationReport invalid = PackValidationReport.of(
                null,
                List.of(ai.docuforge.businesspack.manifest.PackValidationIssue.error(
                        "PACK_FILE_MISSING", "error.pack.file_missing")),
                0,
                0
        );
        when(validationService.validate(any())).thenReturn(invalid);

        PackImportJobResponse bad = service.validate(principal, jobId);
        assertThat(bad.status()).isEqualTo(PackImportJobStatus.INVALID);
        verify(auditService).record(
                eq(principal), eq(AuditActions.PACK_VALIDATION_FAILED), eq("PACK_IMPORT_JOB"), eq(jobId.toString()),
                eq("FAILURE"), any()
        );
    }

    @Test
    void validateExceptionMarksFailed() throws Exception {
        PackImportJob job = baseJob(PackImportJobStatus.UPLOADED);
        when(jobRepository.findByIdAndCompanyId(jobId, companyId)).thenReturn(Optional.of(job));
        when(storageProvider.exists("packimports/a.zip")).thenReturn(true);
        when(storageProvider.read("packimports/a.zip")).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(validationService.validate(any())).thenThrow(new RuntimeException("boom"));

        PackImportJobResponse response = service.validate(principal, jobId);
        assertThat(response.status()).isEqualTo(PackImportJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("PACK_VALIDATION_FAILED");
    }

    @Test
    void expireStaleImportsHandlesNullKeyAndDeleteFailure() {
        PackImportJob withKey = baseJob(PackImportJobStatus.UPLOADED);
        withKey.setId(UUID.randomUUID());
        withKey.setExpiresAt(Instant.now().minusSeconds(60));
        PackImportJob noKey = baseJob(PackImportJobStatus.VALID);
        noKey.setId(UUID.randomUUID());
        noKey.setStagingStorageKey(null);
        noKey.setExpiresAt(Instant.now().minusSeconds(60));

        when(jobRepository.findByExpiresAtBeforeAndStatusIn(any(), any())).thenReturn(List.of(withKey, noKey));
        when(storageProvider.exists("packimports/a.zip")).thenReturn(true);
        doThrow(new RuntimeException("io")).when(storageProvider).delete("packimports/a.zip");
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.expireStaleImports()).isEqualTo(2);
        assertThat(withKey.getStatus()).isEqualTo(PackImportJobStatus.EXPIRED);
        assertThat(noKey.getStatus()).isEqualTo(PackImportJobStatus.EXPIRED);
    }

    @Test
    void looksLikeZipAcceptsSpannedSignature() {
        assertThat(PackImportService.looksLikeZip(new byte[] {0x50, 0x4B, 0x07, 0x08})).isTrue();
    }

    private PackImportService newService(PackProperties props) {
        return new PackImportService(
                props,
                jobRepository,
                companyRepository,
                packRepository,
                storageProvider,
                validationService,
                compatibilityService,
                auditService,
                new ObjectMapper()
        );
    }

    private PackImportJob baseJob(PackImportJobStatus status) {
        PackImportJob job = new PackImportJob();
        job.setId(jobId);
        job.setStatus(status);
        job.setStagingStorageKey("packimports/a.zip");
        job.setOriginalFilename("demo.zip");
        job.setCreatedAt(Instant.now());
        job.setExpiresAt(Instant.now().plusSeconds(3600));
        return job;
    }

    private static MockMultipartFile zipFile(byte[] bytes) {
        return new MockMultipartFile("file", "demo.zip", "application/zip", bytes);
    }
}
