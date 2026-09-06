package ai.docuforge.businesspack.installation;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.archive.PackArchiveContentReader;
import ai.docuforge.businesspack.archive.PackArchiveContentReader.PackArchiveContents;
import ai.docuforge.businesspack.archive.PackArchiveInspector;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.businesspack.dto.PackInstallRequest;
import ai.docuforge.businesspack.dto.PackInstallResponse;
import ai.docuforge.businesspack.manifest.PackManifest;
import ai.docuforge.businesspack.manifest.PackManifestParser;
import ai.docuforge.config.PackProperties;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackInstallation;
import ai.docuforge.domain.businesspack.BusinessPackInstallationRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackVersion;
import ai.docuforge.domain.businesspack.BusinessPackVersionRepository;
import ai.docuforge.domain.businesspack.PackImportJob;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.businesspack.PackImportJobStatus;
import ai.docuforge.domain.businesspack.PackInstallationStatus;
import ai.docuforge.domain.businesspack.PackInstallationType;
import ai.docuforge.storage.StorageCategory;
import ai.docuforge.storage.StoragePathGuard;
import ai.docuforge.storage.StorageProvider;
import ai.docuforge.storage.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pack installation from a {@code VALID} import job (PRD §§72–74, §82).
 * Files are staged via {@link StorageProvider} before the DB transaction; compensated on failure.
 */
@Service
public class PackInstallationService {

    private static final Logger log = LoggerFactory.getLogger(PackInstallationService.class);
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final PackProperties packProperties;
    private final PackImportJobRepository jobRepository;
    private final StorageProvider storageProvider;
    private final PackArchiveInspector archiveInspector;
    private final PackArchiveContentReader contentReader;
    private final PackManifestParser manifestParser;
    private final PackChecksumValidator checksumValidator;
    private final PackInstallationPersistence persistence;
    private final BusinessPackRepository packRepository;
    private final BusinessPackVersionRepository packVersionRepository;
    private final BusinessPackInstallationRepository installationRepository;
    private final AuditService auditService;

    public PackInstallationService(
            PackProperties packProperties,
            PackImportJobRepository jobRepository,
            StorageProvider storageProvider,
            PackArchiveInspector archiveInspector,
            PackArchiveContentReader contentReader,
            PackManifestParser manifestParser,
            PackChecksumValidator checksumValidator,
            PackInstallationPersistence persistence,
            BusinessPackRepository packRepository,
            BusinessPackVersionRepository packVersionRepository,
            BusinessPackInstallationRepository installationRepository,
            AuditService auditService
    ) {
        this.packProperties = packProperties;
        this.jobRepository = jobRepository;
        this.storageProvider = storageProvider;
        this.archiveInspector = archiveInspector;
        this.contentReader = contentReader;
        this.manifestParser = manifestParser;
        this.checksumValidator = checksumValidator;
        this.persistence = persistence;
        this.packRepository = packRepository;
        this.packVersionRepository = packVersionRepository;
        this.installationRepository = installationRepository;
        this.auditService = auditService;
    }

    public PackInstallResponse install(DocuForgePrincipal principal, UUID jobId, PackInstallRequest request) {
        assertPacksEnabled();
        PackInstallRequest options = request == null ? PackInstallRequest.defaults() : request;

        PackImportJob job = jobRepository.findByIdAndCompanyId(jobId, principal.getCompanyId())
                .orElseThrow(() -> notFound("error.pack.import_not_found"));

        if (job.getStatus() == PackImportJobStatus.INSTALLED) {
            return reloadInstalledResponse(principal, job);
        }
        if (job.getStatus() == PackImportJobStatus.INSTALLING) {
            throw conflict("error.pack.import_already_processed");
        }
        if (job.getStatus() != PackImportJobStatus.VALID) {
            throw conflict("error.pack.install_requires_valid");
        }
        if (!StringUtils.hasText(job.getStagingStorageKey()) || !storageProvider.exists(job.getStagingStorageKey())) {
            throw notFound("error.pack.file_missing");
        }

        job.setStatus(PackImportJobStatus.INSTALLING);
        jobRepository.saveAndFlush(job);

        List<String> stagedKeys = new ArrayList<>();
        Path tempZip = null;
        try {
            tempZip = Files.createTempFile("pack-install-", ".zip");
            try (InputStream in = storageProvider.read(job.getStagingStorageKey())) {
                Files.copy(in, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }

            var inspection = archiveInspector.inspect(tempZip);
            PackManifest manifest;
            Map<String, byte[]> logicalBytes = new LinkedHashMap<>();
            try (ZipFile zipFile = ZipFile.builder().setPath(tempZip).get()) {
                PackArchiveContents contents = contentReader.open(zipFile, inspection.entryNames());
                String manifestJson = contents.readUtf8("manifest.json");
                if (!StringUtils.hasText(manifestJson)) {
                    throw badRequest("error.pack.manifest_missing");
                }
                manifest = manifestParser.parse(manifestJson);
                putBytes(contents, logicalBytes, "manifest.json");
                collectInstallBytes(manifest, contents, logicalBytes);
            }

            String archiveChecksum = checksumValidator.digestHex(Files.readAllBytes(tempZip));
            Map<String, String> stagedByLogical = stageFiles(manifest, logicalBytes, stagedKeys);

            String previousVersion = packRepository
                    .findByCompanyIdAndPackKeyWithCurrentVersion(principal.getCompanyId(), manifest.id())
                    .map(BusinessPack::getCurrentVersion)
                    .map(BusinessPackVersion::getVersion)
                    .orElse(null);

            PackInstallResponse response = persistence.persist(
                    principal,
                    jobId,
                    options,
                    manifest,
                    logicalBytes,
                    stagedByLogical,
                    archiveChecksum
            );

            String auditAction = response.installationType() == PackInstallationType.UPDATE
                    ? AuditActions.PACK_UPDATED
                    : AuditActions.PACK_INSTALLED;
            Map<String, Object> auditMeta = new LinkedHashMap<>();
            auditMeta.put("packKey", response.packKey());
            auditMeta.put("version", response.version());
            auditMeta.put("templateCount", response.templatesInstalled());
            auditMeta.put("installationType", response.installationType().name());
            auditMeta.put("jobId", jobId.toString());
            if (previousVersion != null) {
                auditMeta.put("previousVersion", previousVersion);
            }
            auditService.record(
                    principal,
                    auditAction,
                    "BUSINESS_PACK",
                    response.packId().toString(),
                    "SUCCESS",
                    auditMeta
            );
            return response;
        } catch (ResponseStatusException ex) {
            compensate(stagedKeys);
            markJobFailed(job, ex.getReason() == null ? "PACK_INSTALLATION_FAILED" : ex.getStatusCode().toString(),
                    ex.getReason() == null ? "error.pack.installation_failed" : ex.getReason());
            auditFailure(principal, jobId, ex.getReason());
            throw ex;
        } catch (Exception ex) {
            log.warn("Pack installation failed jobId={}", jobId, ex);
            compensate(stagedKeys);
            markJobFailed(job, "PACK_INSTALLATION_FAILED", "error.pack.installation_failed");
            auditFailure(principal, jobId, "exception");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.installation_failed", ex);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private Map<String, String> stageFiles(
            PackManifest manifest,
            Map<String, byte[]> logicalBytes,
            List<String> stagedKeys
    ) throws IOException {
        Map<String, String> staged = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : logicalBytes.entrySet()) {
            String logical = entry.getKey();
            byte[] bytes = entry.getValue();
            if (bytes == null) {
                continue;
            }
            String filename = fileName(logical);
            StoragePathGuard.sanitizeOriginalFilename(filename);
            String contentType = contentTypeFor(filename);
            StorageCategory category = filename.toLowerCase(Locale.ROOT).endsWith(".docx")
                    ? StorageCategory.TEMPLATES
                    : StorageCategory.TEMPORARY;
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                StoredFile stored = storageProvider.store(
                        category,
                        filename,
                        contentType,
                        in,
                        bytes.length
                );
                stagedKeys.add(stored.storageKey());
                staged.put(logical, stored.storageKey());
            }
        }
        if (manifest.templates() != null) {
            for (PackManifest.TemplateEntry template : manifest.templates()) {
                if (template != null && StringUtils.hasText(template.templateFile())
                        && !staged.containsKey(template.templateFile())) {
                    throw badRequest("error.pack.file_missing");
                }
            }
        }
        return staged;
    }

    private static void collectInstallBytes(
            PackManifest manifest,
            PackArchiveContents contents,
            Map<String, byte[]> logicalBytes
    ) throws IOException {
        if (manifest.templates() != null) {
            for (PackManifest.TemplateEntry template : manifest.templates()) {
                if (template == null) {
                    continue;
                }
                putBytes(contents, logicalBytes, template.templateFile());
                putBytes(contents, logicalBytes, template.metadataFile());
                putBytes(contents, logicalBytes, template.previewFile());
            }
        }
        if (manifest.prompts() != null) {
            for (PackManifest.PromptEntry prompt : manifest.prompts()) {
                if (prompt != null) {
                    putBytes(contents, logicalBytes, prompt.file());
                }
            }
        }
        if (manifest.samples() != null) {
            for (PackManifest.SampleEntry sample : manifest.samples()) {
                if (sample != null) {
                    putBytes(contents, logicalBytes, sample.file());
                }
            }
        }
    }

    private static void putBytes(PackArchiveContents contents, Map<String, byte[]> map, String logical)
            throws IOException {
        if (!StringUtils.hasText(logical) || map.containsKey(logical)) {
            return;
        }
        byte[] bytes = contents.readBytes(logical);
        if (bytes == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.file_missing");
        }
        map.put(logical, bytes);
    }

    private PackInstallResponse reloadInstalledResponse(DocuForgePrincipal principal, PackImportJob job) {
        BusinessPack pack = packRepository.findByCompanyIdAndPackKey(
                        principal.getCompanyId(), job.getDetectedPackKey())
                .orElseThrow(() -> notFound("error.pack.import_not_found"));
        BusinessPackVersion version = packVersionRepository
                .findByBusinessPackIdAndVersion(pack.getId(), job.getDetectedVersion())
                .orElseThrow(() -> notFound("error.pack.import_not_found"));
        BusinessPackInstallation installation = installationRepository
                .findFirstByCompanyIdAndBusinessPackIdAndStatusOrderByInstalledAtDesc(
                        principal.getCompanyId(), pack.getId(), PackInstallationStatus.ACTIVE)
                .or(() -> installationRepository.findFirstByCompanyIdAndBusinessPackIdAndStatusOrderByInstalledAtDesc(
                        principal.getCompanyId(), pack.getId(), PackInstallationStatus.DISABLED))
                .orElseThrow(() -> notFound("error.pack.import_not_found"));
        return new PackInstallResponse(
                job.getId(),
                pack.getId(),
                version.getId(),
                installation.getId(),
                pack.getPackKey(),
                version.getVersion(),
                installation.getInstallationType(),
                installation.getStatus(),
                0,
                0
        );
    }

    private void markJobFailed(PackImportJob job, String code, String message) {
        try {
            PackImportJob fresh = jobRepository.findById(job.getId()).orElse(job);
            if (fresh.getStatus() != PackImportJobStatus.INSTALLED) {
                fresh.setStatus(PackImportJobStatus.FAILED);
                fresh.setErrorCode(code);
                fresh.setErrorMessage(message);
                fresh.setCompletedAt(Instant.now());
                jobRepository.save(fresh);
            }
        } catch (Exception ex) {
            log.warn("Unable to mark import job failed id={}", job.getId(), ex);
        }
    }

    private void auditFailure(DocuForgePrincipal principal, UUID jobId, String reason) {
        try {
            auditService.record(
                    principal,
                    AuditActions.PACK_INSTALLATION_FAILED,
                    "PACK_IMPORT_JOB",
                    jobId.toString(),
                    "FAILURE",
                    Map.of("reason", reason == null ? "unknown" : reason)
            );
        } catch (Exception ex) {
            log.warn("Unable to audit pack installation failure", ex);
        }
    }

    private void compensate(List<String> stagedKeys) {
        for (String key : stagedKeys) {
            try {
                if (storageProvider.exists(key)) {
                    storageProvider.delete(key);
                }
            } catch (Exception ex) {
                log.warn("Failed to compensate staged pack file key={}", key, ex);
            }
        }
    }

    private static String fileName(String logicalPath) {
        String normalized = logicalPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            return DOCX_CONTENT_TYPE;
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {
            return "text/plain";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private void assertPacksEnabled() {
        if (!packProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.pack.feature_disabled");
        }
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
