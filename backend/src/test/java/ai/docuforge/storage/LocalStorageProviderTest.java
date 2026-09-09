package ai.docuforge.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.config.StorageProperties;
import ai.docuforge.security.antivirus.AntivirusScanner;
import ai.docuforge.security.antivirus.NoOpAntivirusScanner;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class LocalStorageProviderTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir
    Path tempDir;

    private LocalStorageProvider storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                tempDir.toString(),
                1,
                Set.of("docx", "pdf", "csv"),
                Set.of(DOCX_MIME, "application/pdf", "text/csv", "text/plain", "application/csv")
        );
        storage = new LocalStorageProvider(properties, new NoOpAntivirusScanner());
        storage.init();
    }

    @Test
    void storeReadDeleteRoundTrip() throws Exception {
        byte[] payload = "hello-docx".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = storage.store(
                StorageCategory.TEMPLATES,
                "contrat.docx",
                DOCX_MIME,
                new ByteArrayInputStream(payload),
                payload.length
        );

        assertThat(stored.originalFilename()).isEqualTo("contrat.docx");
        assertThat(stored.extension()).isEqualTo("docx");
        assertThat(stored.storageKey()).startsWith("templates/");
        assertThat(stored.storageKey()).endsWith(".docx");
        assertThat(stored.storageKey()).doesNotContain("contrat");
        assertThat(storage.exists(stored.storageKey())).isTrue();

        try (var in = storage.read(stored.storageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }

        storage.delete(stored.storageKey());
        assertThat(storage.exists(stored.storageKey())).isFalse();
    }

    @Test
    void rejectsPathTraversalInOriginalFilename() {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storage.store(
                StorageCategory.TEMPLATES,
                "../evil.docx",
                DOCX_MIME,
                new ByteArrayInputStream(payload),
                payload.length
        ))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("PATH_TRAVERSAL");
    }

    @Test
    void rejectsPathTraversalInStorageKey() {
        assertThatThrownBy(() -> storage.read("../secrets.txt"))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> {
                    StorageException storageEx = (StorageException) ex;
                    assertThat(storageEx.getCode()).isIn("INVALID_STORAGE_KEY", "PATH_TRAVERSAL");
                });
    }

    @Test
    void rejectsAbsoluteLikeStorageKey() {
        assertThatThrownBy(() -> storage.delete("/etc/passwd"))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_STORAGE_KEY");
    }

    @Test
    void rejectsInvalidExtensionAndMime() {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storage.store(
                StorageCategory.TEMPORARY,
                "malware.exe",
                "application/octet-stream",
                new ByteArrayInputStream(payload),
                payload.length
        ))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_EXTENSION");

        assertThatThrownBy(() -> storage.store(
                StorageCategory.TEMPORARY,
                "ok.docx",
                "application/x-msdownload",
                new ByteArrayInputStream(payload),
                payload.length
        ))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_MIME");
    }

    @Test
    void rejectsOversizedUpload() {
        byte[] payload = new byte[1024 * 1024 + 1];
        assertThatThrownBy(() -> storage.store(
                StorageCategory.GENERATED,
                "big.pdf",
                "application/pdf",
                new ByteArrayInputStream(payload),
                payload.length
        ))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> {
                    StorageException storageEx = (StorageException) ex;
                    assertThat(storageEx.getCode()).isEqualTo("PAYLOAD_TOO_LARGE");
                    assertThat(storageEx.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                });
    }

    @Test
    void storedFileLivesUnderConfiguredRoot() throws Exception {
        byte[] payload = "pdf".getBytes(StandardCharsets.UTF_8);
        StoredFile stored = storage.store(
                StorageCategory.GENERATED,
                "out.pdf",
                "application/pdf",
                new ByteArrayInputStream(payload),
                payload.length
        );
        Path file = tempDir.resolve(stored.storageKey()).normalize();
        assertThat(file.startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void invokesAntivirusBeforeCommit() {
        AtomicBoolean scanned = new AtomicBoolean(false);
        AntivirusScanner scanner = (file, originalFilename) -> {
            scanned.set(true);
            assertThat(Files.exists(file)).isTrue();
        };
        LocalStorageProvider guarded = new LocalStorageProvider(
                new StorageProperties(
                        tempDir.toString(),
                        1,
                        Set.of("docx", "pdf", "csv"),
                        Set.of(DOCX_MIME, "application/pdf", "text/csv", "text/plain", "application/csv")
                ),
                scanner
        );
        guarded.init();

        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        StoredFile stored = guarded.store(
                StorageCategory.TEMPLATES,
                "scan.docx",
                DOCX_MIME,
                new ByteArrayInputStream(payload),
                payload.length
        );

        assertThat(scanned).isTrue();
        assertThat(guarded.exists(stored.storageKey())).isTrue();
    }

    @Test
    void malwareDetectionAbortsStoreWithoutFinalFile() {
        AntivirusScanner scanner = (file, originalFilename) -> {
            throw new StorageException("MALWARE_DETECTED", HttpStatus.UNPROCESSABLE_ENTITY, "error.antivirus.malware");
        };
        LocalStorageProvider guarded = new LocalStorageProvider(
                new StorageProperties(
                        tempDir.toString(),
                        1,
                        Set.of("docx", "pdf", "csv"),
                        Set.of(DOCX_MIME, "application/pdf", "text/csv", "text/plain", "application/csv")
                ),
                scanner
        );
        guarded.init();

        byte[] payload = "evil".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> guarded.store(
                StorageCategory.TEMPLATES,
                "evil.docx",
                DOCX_MIME,
                new ByteArrayInputStream(payload),
                payload.length
        ))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("MALWARE_DETECTED");

        try (var stream = Files.list(tempDir.resolve("templates"))) {
            assertThat(stream.toList()).isEmpty();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void initCreatesAllCategoryDirectories() {
        assertThat(Files.isDirectory(tempDir.resolve("templates"))).isTrue();
        assertThat(Files.isDirectory(tempDir.resolve("generated"))).isTrue();
        assertThat(Files.isDirectory(tempDir.resolve("temporary"))).isTrue();
        assertThat(Files.isDirectory(tempDir.resolve("packimports"))).isTrue();
    }

    @Test
    void readMissingFileThrowsNotFoundAndDeleteIsNoOp() {
        String missingKey = "generated/" + java.util.UUID.randomUUID() + ".pdf";
        assertThatThrownBy(() -> storage.read(missingKey))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("FILE_NOT_FOUND");

        storage.delete(missingKey);
        assertThat(storage.exists(missingKey)).isFalse();
    }

    @Test
    void malformedKeyRejectedOnExistsReadDelete() {
        assertThatThrownBy(() -> storage.exists("bad-key"))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_STORAGE_KEY");
        assertThatThrownBy(() -> storage.read("bad-key"))
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_STORAGE_KEY");
        assertThatThrownBy(() -> storage.delete("bad-key"))
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_STORAGE_KEY");
    }

    @Test
    void storesUnderEachCategoryPrefixAndAcceptsCharsetMime() {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        StoredFile templates = storage.store(
                StorageCategory.TEMPLATES, "a.docx", DOCX_MIME + "; charset=utf-8",
                new ByteArrayInputStream(payload), payload.length
        );
        StoredFile generated = storage.store(
                StorageCategory.GENERATED, "b.pdf", "application/pdf",
                new ByteArrayInputStream(payload), payload.length
        );
        StoredFile temporary = storage.store(
                StorageCategory.TEMPORARY, "c.csv", "text/csv",
                new ByteArrayInputStream(payload), payload.length
        );
        StoredFile packimports = storage.store(
                StorageCategory.PACK_IMPORTS, "d.docx", DOCX_MIME,
                new ByteArrayInputStream(payload), payload.length
        );

        assertThat(templates.storageKey()).startsWith("templates/");
        assertThat(generated.storageKey()).startsWith("generated/");
        assertThat(temporary.storageKey()).startsWith("temporary/");
        assertThat(packimports.storageKey()).startsWith("packimports/");
    }

    @Test
    void rejectsBlankMime() {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storage.store(
                StorageCategory.TEMPORARY,
                "ok.docx",
                "  ",
                new ByteArrayInputStream(payload),
                payload.length
        ))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_MIME");
    }
}