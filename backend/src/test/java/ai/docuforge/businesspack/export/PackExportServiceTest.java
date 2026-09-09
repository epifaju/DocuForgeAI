package ai.docuforge.businesspack.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.businesspack.manifest.PackManifestParser;
import ai.docuforge.config.PackProperties;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackFile;
import ai.docuforge.domain.businesspack.BusinessPackFileRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackType;
import ai.docuforge.domain.businesspack.BusinessPackVersion;
import ai.docuforge.domain.businesspack.PackFileType;
import ai.docuforge.storage.StorageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PackExportServiceTest {

    @Mock private BusinessPackRepository packRepository;
    @Mock private BusinessPackFileRepository packFileRepository;
    @Mock private StorageProvider storageProvider;
    @Mock private AuditService auditService;

    private PackExportService service;
    private DocuForgePrincipal principal;
    private final UUID companyId = UUID.randomUUID();
    private final UUID packId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        service = new PackExportService(
                new PackProperties(true, 10, 50, 100, 20, 10, 50, 24, "0.1.0", Set.of("json", "docx")),
                packRepository,
                packFileRepository,
                storageProvider,
                new PackChecksumValidator(),
                new PackManifestParser(mapper),
                mapper,
                auditService
        );
        principal = new DocuForgePrincipal(
                UUID.randomUUID(), companyId, "pack-co", "admin@pack-co.test", "h", true, Set.of("ADMIN")
        );
    }

    @Test
    void rejectsWhenFeatureDisabled() {
        PackExportService disabled = new PackExportService(
                new PackProperties(false, 10, 50, 100, 20, 10, 50, 24, "0.1.0", Set.of("json")),
                packRepository,
                packFileRepository,
                storageProvider,
                new PackChecksumValidator(),
                new PackManifestParser(new ObjectMapper()),
                new ObjectMapper(),
                auditService
        );
        assertThatThrownBy(() -> disabled.exportPack(principal, packId))
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsMissingPackVersionOrManifest() {
        when(packRepository.findByIdAndCompanyIdWithCurrentVersion(packId, companyId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.exportPack(principal, packId))
                .hasMessageContaining("error.pack.not_found");

        BusinessPack pack = packWithoutVersion();
        when(packRepository.findByIdAndCompanyIdWithCurrentVersion(packId, companyId))
                .thenReturn(Optional.of(pack));
        assertThatThrownBy(() -> service.exportPack(principal, packId))
                .hasMessageContaining("error.pack.export_no_version");

        BusinessPackVersion version = new BusinessPackVersion();
        version.setId(versionId);
        version.setVersion("1.0.0");
        version.setManifest("  ");
        pack.setCurrentVersion(version);
        assertThatThrownBy(() -> service.exportPack(principal, packId))
                .hasMessageContaining("error.pack.manifest_missing");
    }

    @Test
    void rejectsEmptyExportAndMissingStorage() {
        BusinessPack pack = packWithManifest();
        when(packRepository.findByIdAndCompanyIdWithCurrentVersion(packId, companyId))
                .thenReturn(Optional.of(pack));
        when(packFileRepository.findByBusinessPackVersionId(versionId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.exportPack(principal, packId))
                .hasMessageContaining("error.pack.export_empty");

        BusinessPackFile missingKey = file("templates/a.docx", null);
        when(packFileRepository.findByBusinessPackVersionId(versionId)).thenReturn(List.of(missingKey));
        assertThatThrownBy(() -> service.exportPack(principal, packId))
                .hasMessageContaining("error.pack.file_missing");
    }

    @Test
    void exportRebuildsZipWithRecalculatedChecksums() throws Exception {
        BusinessPack pack = packWithManifest();
        pack.setSlug("@@@");
        byte[] docx = "docx-bytes".getBytes(StandardCharsets.UTF_8);
        BusinessPackFile template = file("templates/demo.docx", "generated/demo.docx");
        BusinessPackFile manifestOnly = file("manifest.json", "generated/manifest.json");

        when(packRepository.findByIdAndCompanyIdWithCurrentVersion(packId, companyId))
                .thenReturn(Optional.of(pack));
        when(packFileRepository.findByBusinessPackVersionId(versionId))
                .thenReturn(List.of(template, manifestOnly));
        when(storageProvider.read("generated/demo.docx")).thenReturn(new ByteArrayInputStream(docx));

        PackExportDownload download = service.exportPack(principal, packId);

        assertThat(download.filename()).isEqualTo("1.0.0.zip");
        assertThat(download.contentType()).isEqualTo("application/zip");
        assertThat(zipContains(download.body().getInputStream(), "manifest.json")).isTrue();
        assertThat(zipContains(download.body().getInputStream(), "templates/demo.docx")).isTrue();
        verify(auditService).record(
                eq(principal), eq(AuditActions.PACK_EXPORTED), eq("BUSINESS_PACK"), eq(packId.toString()),
                eq("SUCCESS"), any()
        );
    }

    @Test
    void invalidManifestJsonFails() {
        BusinessPack pack = packWithManifest();
        pack.getCurrentVersion().setManifest("{not-json");
        when(packRepository.findByIdAndCompanyIdWithCurrentVersion(packId, companyId))
                .thenReturn(Optional.of(pack));
        when(packFileRepository.findByBusinessPackVersionId(versionId))
                .thenReturn(List.of(file("templates/demo.docx", "k")));
        when(storageProvider.read("k")).thenReturn(new ByteArrayInputStream(new byte[] {1}));

        assertThatThrownBy(() -> service.exportPack(principal, packId))
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private BusinessPack packWithoutVersion() {
        BusinessPack pack = new BusinessPack();
        pack.setId(packId);
        pack.setPackKey("com.demo");
        pack.setSlug("demo");
        pack.setName("Demo");
        pack.setPackType(BusinessPackType.CUSTOM);
        return pack;
    }

    private BusinessPack packWithManifest() {
        BusinessPack pack = packWithoutVersion();
        BusinessPackVersion version = new BusinessPackVersion();
        version.setId(versionId);
        version.setVersion("1.0.0");
        version.setManifest("""
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.demo",
                  "name": "Demo",
                  "slug": "demo",
                  "version": "1.0.0",
                  "type": "CUSTOM",
                  "description": "d",
                  "publisher": { "id": "p", "name": "P" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [],
                  "checksums": {}
                }
                """);
        pack.setCurrentVersion(version);
        return pack;
    }

    private static BusinessPackFile file(String path, String storageKey) {
        BusinessPackFile file = new BusinessPackFile();
        file.setLogicalPath(path);
        file.setStorageKey(storageKey);
        file.setFileType(PackFileType.TEMPLATE);
        file.setChecksum("sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        file.setSizeBytes(1);
        return file;
    }

    private static boolean zipContains(InputStream in, String name) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
