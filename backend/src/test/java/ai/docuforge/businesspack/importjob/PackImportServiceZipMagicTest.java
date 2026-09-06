package ai.docuforge.businesspack.importjob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackImportServiceZipMagicTest {

    @Test
    void acceptsLocalFileHeader() {
        assertThat(PackImportService.looksLikeZip(new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00})).isTrue();
    }

    @Test
    void acceptsEmptyArchiveEndOfCentralDirectory() {
        assertThat(PackImportService.looksLikeZip(new byte[] {0x50, 0x4B, 0x05, 0x06})).isTrue();
    }

    @Test
    void rejectsMimeSpoofAndShortPayload() {
        assertThat(PackImportService.looksLikeZip("not-a-zip".getBytes())).isFalse();
        assertThat(PackImportService.looksLikeZip(new byte[] {0x50, 0x4B})).isFalse();
        assertThat(PackImportService.looksLikeZip(null)).isFalse();
        assertThat(PackImportService.looksLikeZip(new byte[] {0x00, 0x00, 0x00, 0x00})).isFalse();
    }
}
