package ai.docuforge.businesspack.importjob;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.compatibility.PackCompatibilityService;
import ai.docuforge.businesspack.dto.PackImportJobResponse;
import ai.docuforge.businesspack.validation.PackValidationReport;
import ai.docuforge.businesspack.validation.PackValidationService;
import ai.docuforge.config.PackProperties;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.PackImportJob;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.businesspack.PackImportJobStatus;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StoragePathGuard;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pack ZIP import staging + sync validation (PRD §§77–81, Phase 9). No installation.
 */
@Service
public class PackImportService {

    private static final Logger log = LoggerFactory.getLogger(PackImportService.class);
    private static final EnumSet<PackImportJobStatus> EXPIREABLE = EnumSet.of(
            PackImportJobStatus.UPLOADED,
            PackImportJobStatus.SCANNING,
            PackImportJobStatus.VALIDATING,
            PackImportJobStatus.VALID,
            PackImportJobStatus.INVALID,
            PackImportJobStatus.FAILED
    );

    private final PackProperties packProperties;
    private final PackImportJobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final BusinessPackRepository packRepository;
    private final StorageProvider storageProvider;
    private final PackValidationService validationService;
    private final PackCompatibilityService compatibilityService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PackImportService(
            PackProperties packProperties,
            PackImportJobRepository jobRepository,
            CompanyRepository companyRepository,
            BusinessPackRepository packRepository,
            StorageProvider storageProvider,
            PackValidationService validationService,
            PackCompatibilityService compatibilityService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.packProperties = packProperties;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.packRepository = packRepository;
        this.storageProvider = storageProvider;
        this.validationService = validationService;
        this.compatibilityService = compatibilityService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PackImportJobResponse upload(DocuForgePrincipal principal, MultipartFile file) {
        assertPacksEnabled();
        if (file == null || file.isEmpty()) {
            throw badRequest("error.pack.import_file_required");
        }
        String filename = file.getOriginalFilename() == null ? "pack.zip" : file.getOriginalFilename();
        StoragePathGuard.sanitizeOriginalFilename(filename);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw badRequest("error.pack.import_zip_required");
        }
        if (file.getSize() > packProperties.maxUploadBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "error.pack.file_too_large");
        }

        byte[] payload;
        try {
            payload = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.pack.archive_invalid", ex);
        }
        if (!looksLikeZip(payload)) {
            throw badRequest("error.pack.import_zip_required");
        }

        StoredFile stored;
        try (InputStream in = new ByteArrayInputStream(payload)) {
            stored = storageProvider.store(
                    StorageCategory.PACK_IMPORTS,
                    filename,
                    "application/zip",
                    in,
                    payload.length
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.pack.archive_invalid", ex);
        }

        PackImportJob job = new PackImportJob();
        job.setCompany(companyRepository.getReferenceById(principal.getCompanyId()));
        job.setOriginalFilename(stored.originalFilename());
        job.setStagingStorageKey(stored.storageKey());
        job.setStatus(PackImportJobStatus.UPLOADED);
        job.setUploadedBy(principal.getUserId());
        job.setExpiresAt(Instant.now().plus(packProperties.importRetentionHours(), ChronoUnit.HOURS));
        job = jobRepository.save(job);

        auditService.record(
                principal,
                AuditActions.PACK_UPLOADED,
                "PACK_IMPORT_JOB",
                job.getId().toString(),
                "SUCCESS",
                Map.of(
                        "originalFilename", stored.originalFilename(),
                        "sizeBytes", stored.sizeBytes()
                )
        );

        return toResponse(job, null);
    }

    /**
     * ZIP local/central/empty signatures (PRD Phase 22 MIME / spoof hardening).
     */
    static boolean looksLikeZip(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return false;
        }
        return payload[0] == 0x50
                && payload[1] == 0x4B
                && ((payload[2] == 0x03 && payload[3] == 0x04)
                        || (payload[2] == 0x05 && payload[3] == 0x06)
                        || (payload[2] == 0x07 && payload[3] == 0x08));
    }

    @Transactional(readOnly = true)
    public PackImportJobResponse get(DocuForgePrincipal principal, UUID jobId) {
        assertPacksEnabled();
        PackImportJob job = requireJob(principal, jobId);
        return toResponse(job, parseReport(job.getValidationReport()));
    }

    @Transactional
    public PackImportJobResponse validate(DocuForgePrincipal principal, UUID jobId) {
        assertPacksEnabled();
        PackImportJob job = requireJob(principal, jobId);
        if (job.getStatus() == PackImportJobStatus.EXPIRED) {
            throw conflict("error.pack.import_expired");
        }
        if (job.getStatus() == PackImportJobStatus.INSTALLED || job.getStatus() == PackImportJobStatus.INSTALLING) {
            throw conflict("error.pack.import_already_processed");
        }
        if (!storageProvider.exists(job.getStagingStorageKey())) {
            job.setStatus(PackImportJobStatus.FAILED);
            job.setErrorCode("PACK_FILE_MISSING");
            job.setErrorMessage("error.pack.file_missing");
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            throw notFound("error.pack.file_missing");
        }

        job.setStatus(PackImportJobStatus.VALIDATING);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        jobRepository.saveAndFlush(job);

        Path temp = null;
        try {
            temp = Files.createTempFile("pack-import-", ".zip");
            try (InputStream in = storageProvider.read(job.getStagingStorageKey())) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }

            PackValidationReport report = validationService.validate(temp);
            job.setValidationReport(objectMapper.writeValueAsString(report));
            if (report.pack() != null) {
                job.setDetectedPackKey(report.pack().id());
                job.setDetectedVersion(report.pack().version());
            }
            job.setCompletedAt(Instant.now());
            if (report.valid()) {
                job.setStatus(PackImportJobStatus.VALID);
                jobRepository.save(job);
                markUpdateAvailableIfApplicable(principal.getCompanyId(), report);
                auditService.record(
                        principal,
                        AuditActions.PACK_VALIDATED,
                        "PACK_IMPORT_JOB",
                        job.getId().toString(),
                        "SUCCESS",
                        Map.of(
                                "packKey", report.pack() == null ? "" : nullToEmpty(report.pack().id()),
                                "version", report.pack() == null ? "" : nullToEmpty(report.pack().version()),
                                "templateCount", report.summary().templates()
                        )
                );
            } else {
                job.setStatus(PackImportJobStatus.INVALID);
                job.setErrorCode("PACK_VALIDATION_FAILED");
                job.setErrorMessage("error.pack.validation_failed");
                jobRepository.save(job);
                auditService.record(
                        principal,
                        AuditActions.PACK_VALIDATION_FAILED,
                        "PACK_IMPORT_JOB",
                        job.getId().toString(),
                        "FAILURE",
                        Map.of(
                                "errors", report.summary().errors(),
                                "warnings", report.summary().warnings()
                        )
                );
            }
            return toResponse(job, report);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Pack import validation failed jobId={}", jobId, ex);
            job.setStatus(PackImportJobStatus.FAILED);
            job.setErrorCode("PACK_VALIDATION_FAILED");
            job.setErrorMessage("error.pack.validation_failed");
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            auditService.record(
                    principal,
                    AuditActions.PACK_VALIDATION_FAILED,
                    "PACK_IMPORT_JOB",
                    job.getId().toString(),
                    "FAILURE",
                    Map.of("reason", "exception")
            );
            return toResponse(job, parseReport(job.getValidationReport()));
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    @Transactional
    public int expireStaleImports() {
        Instant now = Instant.now();
        List<PackImportJob> stale = jobRepository.findByExpiresAtBeforeAndStatusIn(now, EXPIREABLE);
        int count = 0;
        for (PackImportJob job : stale) {
            deleteStagingQuietly(job.getStagingStorageKey());
            job.setStagingStorageKey(null);
            job.setStatus(PackImportJobStatus.EXPIRED);
            job.setCompletedAt(now);
            job.setErrorCode("PACK_IMPORT_EXPIRED");
            job.setErrorMessage("error.pack.import_expired");
            jobRepository.save(job);
            count++;
        }
        if (count > 0) {
            log.info("Expired {} pack import jobs", count);
        }
        return count;
    }

    private void markUpdateAvailableIfApplicable(UUID companyId, PackValidationReport report) {
        if (report.pack() == null
                || !StringUtils.hasText(report.pack().id())
                || !StringUtils.hasText(report.pack().version())) {
            return;
        }
        packRepository.findByCompanyIdAndPackKeyWithCurrentVersion(companyId, report.pack().id()).ifPresent(pack -> {
            if (pack.getStatus() != BusinessPackStatus.INSTALLED
                    && pack.getStatus() != BusinessPackStatus.UPDATE_AVAILABLE) {
                return;
            }
            if (pack.getCurrentVersion() == null) {
                return;
            }
            if (compatibilityService.isUpgrade(pack.getCurrentVersion().getVersion(), report.pack().version())) {
                pack.setStatus(BusinessPackStatus.UPDATE_AVAILABLE);
                packRepository.save(pack);
            }
        });
    }

    private PackImportJob requireJob(DocuForgePrincipal principal, UUID jobId) {
        return jobRepository.findByIdAndCompanyId(jobId, principal.getCompanyId())
                .orElseThrow(() -> notFound("error.pack.import_not_found"));
    }

    private void assertPacksEnabled() {
        if (!packProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.pack.feature_disabled");
        }
    }

    private void deleteStagingQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            if (storageProvider.exists(storageKey)) {
                storageProvider.delete(storageKey);
            }
        } catch (Exception ex) {
            log.warn("Failed to delete staged pack archive key={}", storageKey, ex);
        }
    }

    private PackImportJobResponse toResponse(PackImportJob job, Object report) {
        Object validationReport = report;
        if (validationReport == null) {
            validationReport = parseReport(job.getValidationReport());
        }
        return new PackImportJobResponse(
                job.getId(),
                job.getStatus(),
                job.getOriginalFilename(),
                job.getDetectedPackKey(),
                job.getDetectedVersion(),
                validationReport,
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                job.getExpiresAt()
        );
    }

    private Object parseReport(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException ex) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
