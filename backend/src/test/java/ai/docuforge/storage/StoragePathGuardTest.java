package ai.docuforge.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StoragePathGuardTest {

    @Test
    void rejectsBlankNullAndTraversalFilenames() {
        assertThatThrownBy(() -> StoragePathGuard.sanitizeOriginalFilename(null))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_FILENAME");

        assertThatThrownBy(() -> StoragePathGuard.sanitizeOriginalFilename("  "))
                .isInstanceOf(StorageException.class);

        assertThatThrownBy(() -> StoragePathGuard.sanitizeOriginalFilename("../x.docx"))
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("PATH_TRAVERSAL");

        assertThatThrownBy(() -> StoragePathGuard.sanitizeOriginalFilename("a\\b.docx"))
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("PATH_TRAVERSAL");

        assertThatThrownBy(() -> StoragePathGuard.sanitizeOriginalFilename("a\0b.docx"))
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("PATH_TRAVERSAL");

        assertThatThrownBy(() -> StoragePathGuard.sanitizeOriginalFilename("x".repeat(256) + ".docx"))
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_FILENAME");
    }

    @Test
    void rejectsMissingExtension() {
        assertThatThrownBy(() -> StoragePathGuard.extractExtension("readme"))
                .isInstanceOf(StorageException.class)
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_EXTENSION");

        assertThatThrownBy(() -> StoragePathGuard.extractExtension("file."))
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_EXTENSION");

        assertThat(StoragePathGuard.extractExtension("Doc.PDF")).isEqualTo("pdf");
    }

    @Test
    void assertSafeStorageKeyRejectsMalformed() {
        assertThatThrownBy(() -> StoragePathGuard.assertSafeStorageKey(""))
                .isInstanceOf(StorageException.class)
                .satisfies(ex -> {
                    StorageException se = (StorageException) ex;
                    assertThat(se.getCode()).isEqualTo("INVALID_STORAGE_KEY");
                    assertThat(se.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        assertThatThrownBy(() -> StoragePathGuard.assertSafeStorageKey("templates/not-a-uuid.docx"))
                .extracting(ex -> ((StorageException) ex).getCode())
                .isEqualTo("INVALID_STORAGE_KEY");
    }
}
