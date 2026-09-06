package ai.docuforge.businesspack.export;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.businesspack.manifest.PackManifest;
import ai.docuforge.businesspack.manifest.PackManifestException;
import ai.docuforge.businesspack.manifest.PackManifestParser;
import ai.docuforge.config.PackProperties;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackFile;
import ai.docuforge.domain.businesspack.BusinessPackFileRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackVersion;
import ai.docuforge.storage.StorageProvider;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rebuilds a valid DBPF-1 ZIP from the installed current pack version (PRD §§97–98).
 * Checksums are always recalculated from StorageProvider bytes.
 */
@Service
public class PackExportService {

    private static final String MANIFEST_PATH = "manifest.json";
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final PackProperties packProperties;
    private final BusinessPackRepository packRepository;
    private final BusinessPackFileRepository packFileRepository;
    private final StorageProvider storageProvider;
    private final PackChecksumValidator checksumValidator;
    private final PackManifestParser manifestParser;
    private final ObjectWriter manifestWriter;
    private final AuditService auditService;

    public PackExportService(
            PackProperties packProperties,
            BusinessPackRepository packRepository,
            BusinessPackFileRepository packFileRepository,
            StorageProvider storageProvider,
            PackChecksumValidator checksumValidator,
            PackManifestParser manifestParser,
            ObjectMapper objectMapper,
            AuditService auditService
    ) {
        this.packProperties = packProperties;
        this.packRepository = packRepository;
        this.packFileRepository = packFileRepository;
        this.storageProvider = storageProvider;
        this.checksumValidator = checksumValidator;
        this.manifestParser = manifestParser;
        this.manifestWriter = objectMapper
                .copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .writerWithDefaultPrettyPrinter();
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PackExportDownload exportPack(DocuForgePrincipal principal, UUID packId) {
        assertPacksEnabled();
        BusinessPack pack = packRepository
                .findByIdAndCompanyIdWithCurrentVersion(packId, principal.getCompanyId())
                .orElseThrow(() -> notFound("error.pack.not_found"));

        BusinessPackVersion version = pack.getCurrentVersion();
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.export_no_version");
        }
        if (!StringUtils.hasText(version.getManifest())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.manifest_missing");
        }

        List<BusinessPackFile> files = packFileRepository.findByBusinessPackVersionId(version.getId());
        Map<String, byte[]> contentByPath = new LinkedHashMap<>();
        Map<String, String> recalculatedChecksums = new LinkedHashMap<>();

        for (BusinessPackFile file : files) {
            if (file == null || !StringUtils.hasText(file.getLogicalPath())) {
                continue;
            }
            String logical = normalizePath(file.getLogicalPath());
            if (MANIFEST_PATH.equalsIgnoreCase(logical)) {
                continue;
            }
            if (!StringUtils.hasText(file.getStorageKey())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.file_missing");
            }
            byte[] bytes = readStorage(file.getStorageKey());
            contentByPath.put(logical, bytes);
            recalculatedChecksums.put(logical, checksumValidator.digestPrefixed(bytes));
        }

        if (contentByPath.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.export_empty");
        }

        byte[] manifestBytes = rebuildManifest(version.getManifest(), recalculatedChecksums);
        byte[] zipBytes = writeZip(manifestBytes, contentByPath);

        String filename = buildFilename(pack, version);
        auditService.record(
                principal,
                AuditActions.PACK_EXPORTED,
                "BUSINESS_PACK",
                pack.getId().toString(),
                "SUCCESS",
                Map.of(
                        "packKey", pack.getPackKey(),
                        "version", version.getVersion(),
                        "fileCount", contentByPath.size(),
                        "archiveBytes", zipBytes.length
                )
        );

        return new PackExportDownload(
                filename,
                ZIP_CONTENT_TYPE,
                new ByteArrayResource(zipBytes),
                zipBytes.length
        );
    }

    private byte[] rebuildManifest(String storedManifest, Map<String, String> checksums) {
        try {
            PackManifest original = manifestParser.parse(storedManifest);
            PackManifest exported = new PackManifest(
                    original.schemaVersion(),
                    original.id(),
                    original.name(),
                    original.slug(),
                    original.version(),
                    original.type(),
                    original.description(),
                    original.publisher(),
                    original.compatibility(),
                    original.locales(),
                    original.defaultLocale(),
                    original.categories(),
                    original.tags(),
                    original.templates(),
                    original.prompts(),
                    original.samples(),
                    Map.copyOf(checksums)
            );
            return manifestWriter.writeValueAsBytes(exported);
        } catch (PackManifestException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.manifest_invalid_json", ex);
        }
    }

    private byte[] writeZip(byte[] manifestBytes, Map<String, byte[]> contentByPath) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(bos)) {
                put(out, MANIFEST_PATH, manifestBytes);
                for (Map.Entry<String, byte[]> entry : contentByPath.entrySet()) {
                    put(out, entry.getKey(), entry.getValue());
                }
            }
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.export_failed", ex);
        }
    }

    private byte[] readStorage(String storageKey) {
        try (InputStream in = storageProvider.read(storageKey)) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.file_missing", ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.file_missing", ex);
        }
    }

    private static void put(ZipArchiveOutputStream out, String name, byte[] data) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        out.putArchiveEntry(entry);
        out.write(data == null ? new byte[0] : data);
        out.closeArchiveEntry();
    }

    private static String buildFilename(BusinessPack pack, BusinessPackVersion version) {
        String base = StringUtils.hasText(pack.getSlug()) ? pack.getSlug() : pack.getPackKey();
        String safe = sanitizeFilename(base + "-" + version.getVersion());
        return safe + ".zip";
    }

    private static String sanitizeFilename(String value) {
        String cleaned = value.replaceAll("[^a-zA-Z0-9._-]+", "-");
        cleaned = cleaned.replaceAll("-{2,}", "-");
        cleaned = cleaned.replaceAll("^[.-]+|[.-]+$", "");
        if (!StringUtils.hasText(cleaned)) {
            return "pack-export";
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/').trim();
    }

    private void assertPacksEnabled() {
        if (!packProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.pack.feature_disabled");
        }
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
