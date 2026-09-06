package ai.docuforge.businesspack.demo;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline structural checks for the artisan demo pack factory (PRD §§161–164).
 */
class ArtisanDemoPackFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsZipWithRequiredDemoEntriesAndManifestIdentity() throws Exception {
        PackChecksumValidator checksumValidator = new PackChecksumValidator();
        byte[] zipBytes = ArtisanDemoPackFactory.buildZip(checksumValidator);
        assertThat(zipBytes.length).isGreaterThan(1000);

        Path zipPath = tempDir.resolve(ArtisanDemoPackFactory.RECOMMENDED_FILENAME);
        Files.write(zipPath, zipBytes);

        try (ZipFile zipFile = ZipFile.builder().setPath(zipPath).get()) {
            Set<String> names = new HashSet<>();
            var entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                names.add(entry.getName());
            }

            assertThat(names).contains(
                    "manifest.json",
                    "templates/artisan-devis.docx",
                    "templates/artisan-intervention.docx",
                    "templates/artisan-completion-certificate.docx",
                    "metadata/artisan-devis.json",
                    "metadata/artisan-intervention.json",
                    "metadata/artisan-completion-certificate.json",
                    "prompts/work-description.txt",
                    "prompts/intervention-notes.txt",
                    "samples/artisan-devis.json",
                    "samples/artisan-intervention.csv",
                    "previews/artisan-devis.png",
                    "previews/artisan-intervention.png",
                    "previews/artisan-completion-certificate.png",
                    "README.md"
            );

            String manifest = new String(
                    zipFile.getInputStream(zipFile.getEntry("manifest.json")).readAllBytes(),
                    StandardCharsets.UTF_8
            );
            assertThat(manifest).contains(ArtisanDemoPackFactory.PACK_ID);
            assertThat(manifest).contains("\"slug\": \"artisan-demo\"");
            assertThat(manifest).contains("ARTISAN_DEVIS");
            assertThat(manifest).contains("ARTISAN_INTERVENTION");
            assertThat(manifest).contains("ARTISAN_COMPLETION_CERTIFICATE");
            assertThat(manifest).doesNotContain("@example.com");
            assertThat(manifest).doesNotContain("password");
        }
    }
}
