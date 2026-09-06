package ai.docuforge.businesspack.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.businesspack.schema.DbpfSchemaSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackManifestValidatorTest {

    private PackManifestParser parser;
    private PackManifestValidator validator;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        parser = new PackManifestParser(mapper);
        validator = new PackManifestValidator(parser, new DbpfSchemaSupport(mapper));
    }

    @Test
    void validFixturePasses() throws IOException {
        PackManifestValidationResult result = validator.validate(readFixture("valid-manifest.json"));
        assertThat(result.valid()).isTrue();
        assertThat(result.manifest()).isNotNull();
        assertThat(result.manifest().id()).isEqualTo("com.docuforge.pack.artisan");
        assertThat(result.summary().templates()).isEqualTo(1);
        assertThat(result.summary().prompts()).isEqualTo(1);
        assertThat(result.summary().errors()).isZero();
    }

    @Test
    void missingManifestThrows() {
        assertThatThrownBy(() -> validator.validate("  "))
                .isInstanceOf(PackManifestException.class)
                .extracting(ex -> ((PackManifestException) ex).getCode())
                .isEqualTo("PACK_MANIFEST_MISSING");
    }

    @Test
    void invalidJsonThrows() {
        assertThatThrownBy(() -> validator.validate("{not-json"))
                .isInstanceOf(PackManifestException.class)
                .extracting(ex -> ((PackManifestException) ex).getCode())
                .isEqualTo("PACK_MANIFEST_INVALID_JSON");
    }

    @Test
    void wrongSchemaVersionFails() throws IOException {
        String json = baseManifest().replace("\"DBPF-1\"", "\"DBPF-0\"");
        PackManifestValidationResult result = validator.validate(json);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "PACK_SCHEMA_UNSUPPORTED".equals(i.code()));
    }

    @Test
    void missingIdFails() throws IOException {
        String json = baseManifest().replace("\"id\": \"com.docuforge.pack.artisan\",\n  ", "");
        PackManifestValidationResult result = validator.validate(json);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues().stream().map(PackValidationIssue::code))
                .anyMatch(code -> code.equals("PACK_ID_INVALID") || code.equals("PACK_MANIFEST_SCHEMA_INVALID"));
    }

    @Test
    void invalidIdFails() throws IOException {
        String json = baseManifest().replace("com.docuforge.pack.artisan", "BadId");
        PackManifestValidationResult result = validator.validate(json);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "PACK_ID_INVALID".equals(i.code())
                || "PACK_MANIFEST_SCHEMA_INVALID".equals(i.code()));
    }

    @Test
    void invalidSemVerFails() throws IOException {
        String json = baseManifest().replace("\"version\": \"1.0.0\"", "\"version\": \"v1\"");
        PackManifestValidationResult result = validator.validate(json);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "PACK_VERSION_INVALID".equals(i.code())
                || "PACK_MANIFEST_SCHEMA_INVALID".equals(i.code()));
    }

    @Test
    void defaultLocaleNotInLocalesFails() throws IOException {
        String json = baseManifest()
                .replace("\"locales\": [\"fr-FR\"]", "\"locales\": [\"fr-FR\"]")
                .replace("\"defaultLocale\": \"fr-FR\"", "\"defaultLocale\": \"pt-PT\"");
        PackManifestValidationResult result = validator.validate(json);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "PACK_LOCALE_INVALID".equals(i.code()));
    }

    @Test
    void duplicateTemplateCodeFails() {
        String json = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.dup",
                  "name": "Dup",
                  "slug": "dup",
                  "version": "1.0.0",
                  "type": "OFFICIAL",
                  "description": "dup templates",
                  "publisher": { "id": "docuforge", "name": "DocuForge AI" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "SAME_CODE",
                      "name": "A",
                      "version": "1.0.0",
                      "templateFile": "templates/a.docx",
                      "metadataFile": "metadata/a.json"
                    },
                    {
                      "code": "SAME_CODE",
                      "name": "B",
                      "version": "1.0.0",
                      "templateFile": "templates/b.docx",
                      "metadataFile": "metadata/b.json"
                    }
                  ],
                  "checksums": {
                    "templates/a.docx": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "metadata/a.json": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "templates/b.docx": "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                    "metadata/b.json": "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                  }
                }
                """;
        PackManifestValidationResult result = validator.validate(json);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "PACK_TEMPLATE_CODE_DUPLICATED".equals(i.code()));
    }

    @Test
    void duplicatePromptCodeFails() {
        String json = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.prompts",
                  "name": "Prompts",
                  "slug": "prompts",
                  "version": "1.0.0",
                  "type": "CUSTOM",
                  "description": "dup prompts",
                  "publisher": { "id": "acme", "name": "ACME" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "TEMPLATE_ONE",
                      "name": "T1",
                      "version": "1.0.0",
                      "templateFile": "templates/t1.docx",
                      "metadataFile": "metadata/t1.json"
                    }
                  ],
                  "prompts": [
                    { "code": "PROMPT_SAME", "version": "1.0.0", "file": "prompts/a.txt" },
                    { "code": "PROMPT_SAME", "version": "1.0.1", "file": "prompts/b.txt" }
                  ],
                  "checksums": {
                    "templates/t1.docx": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "metadata/t1.json": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "prompts/a.txt": "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                    "prompts/b.txt": "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                  }
                }
                """;
        PackManifestValidationResult result = validator.validate(json);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(i -> "PACK_PROMPT_DUPLICATED".equals(i.code()));
    }

    @Test
    void unknownTopLevelFieldProducesWarningButCanRemainValid() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var tree = mapper.readTree(readFixture("valid-manifest.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("experimentalFlag", true);
        PackManifestValidationResult result = validator.validateTree(tree);
        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).anyMatch(i ->
                i.severity() == PackValidationSeverity.WARNING
                        && "PACK_MANIFEST_UNKNOWN_FIELD".equals(i.code()));
    }

    @Test
    void parserMapsValidFixtureToDto() throws IOException {
        PackManifest manifest = parser.parse(readFixture("valid-manifest.json"));
        assertThat(manifest.slug()).isEqualTo("artisan");
        assertThat(manifest.templates()).hasSize(1);
        assertThat(manifest.checksums()).containsKey("templates/artisan-devis.docx");
    }

    private static String baseManifest() throws IOException {
        return readFixture("valid-manifest.json");
    }

    private static String readFixture(String name) throws IOException {
        try (InputStream in = PackManifestValidatorTest.class.getResourceAsStream("/packs/dbpf/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
