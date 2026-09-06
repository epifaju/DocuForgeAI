package ai.docuforge.businesspack.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackManifestParserTest {

    private PackManifestParser parser;

    @BeforeEach
    void setUp() {
        parser = new PackManifestParser(new ObjectMapper());
    }

    @Test
    void parseValidFixture() throws IOException {
        PackManifest manifest = parser.parse(readFixture("valid-manifest.json"));
        assertThat(manifest.schemaVersion()).isEqualTo("DBPF-1");
        assertThat(manifest.id()).isEqualTo("com.docuforge.pack.artisan");
        assertThat(manifest.templates()).hasSize(1);
        assertThat(manifest.prompts()).hasSize(1);
        assertThat(manifest.checksums()).isNotEmpty();
    }

    @Test
    void blankJsonThrowsMissing() {
        assertThatThrownBy(() -> parser.readTree(" "))
                .isInstanceOf(PackManifestException.class)
                .extracting(ex -> ((PackManifestException) ex).getCode())
                .isEqualTo("PACK_MANIFEST_MISSING");
    }

    @Test
    void invalidJsonThrows() {
        assertThatThrownBy(() -> parser.readTree("{"))
                .isInstanceOf(PackManifestException.class)
                .extracting(ex -> ((PackManifestException) ex).getCode())
                .isEqualTo("PACK_MANIFEST_INVALID_JSON");
    }

    @Test
    void nonObjectJsonThrows() {
        assertThatThrownBy(() -> parser.readTree("[1,2]"))
                .isInstanceOf(PackManifestException.class)
                .extracting(ex -> ((PackManifestException) ex).getCode())
                .isEqualTo("PACK_MANIFEST_INVALID_JSON");
    }

    private static String readFixture(String name) throws IOException {
        try (InputStream in = PackManifestParserTest.class.getResourceAsStream("/packs/dbpf/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
