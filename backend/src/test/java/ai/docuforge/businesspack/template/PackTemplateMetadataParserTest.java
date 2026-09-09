package ai.docuforge.businesspack.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.businesspack.manifest.PackManifestException;
import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.manifest.PackValidationSeverity;
import ai.docuforge.businesspack.schema.DbpfSchemaSupport;
import ai.docuforge.domain.template.VariableType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackTemplateMetadataParserTest {

    private PackTemplateMetadataParser parser;

    @BeforeEach
    void setUp() {
        parser = new PackTemplateMetadataParser(new DbpfSchemaSupport(new ObjectMapper()));
    }

    @Test
    void readTreeRejectsNullEmptyAndBlank() {
        assertThatThrownBy(() -> parser.readTree((byte[]) null))
                .isInstanceOf(PackManifestException.class)
                .satisfies(ex -> {
                    PackManifestException pex = (PackManifestException) ex;
                    assertThat(pex.getMessage()).isEqualTo("error.pack.template_metadata_missing");
                });
        assertThatThrownBy(() -> parser.readTree(new byte[0]))
                .isInstanceOf(PackManifestException.class)
                .hasMessage("error.pack.template_metadata_missing");
        assertThatThrownBy(() -> parser.readTree("   "))
                .isInstanceOf(PackManifestException.class)
                .hasMessage("error.pack.template_metadata_missing");
    }

    @Test
    void readTreeRejectsInvalidJsonAndNonObject() {
        assertThatThrownBy(() -> parser.readTree("{not-json"))
                .isInstanceOf(PackManifestException.class)
                .hasMessage("error.pack.template_invalid");
        assertThatThrownBy(() -> parser.readTree("[]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PackManifestException.class)
                .hasMessage("error.pack.template_invalid");
        assertThatThrownBy(() -> parser.readTree("\"x\""))
                .isInstanceOf(PackManifestException.class)
                .hasMessage("error.pack.template_invalid");
        assertThatThrownBy(() -> parser.readTree("null"))
                .isInstanceOf(PackManifestException.class)
                .hasMessage("error.pack.template_invalid");
    }

    @Test
    void validateSchemaInvalidReturnsEarlyWithoutSemanticChecks() throws Exception {
        byte[] badType = readFixture("invalid-template-metadata-bad-type.json");
        List<PackValidationIssue> issues = parser.validate("metadata/bad.json", "ARTISAN_DEVIS", badType);

        assertThat(issues).isNotEmpty();
        assertThat(issues).allMatch(i ->
                i.severity() == PackValidationSeverity.ERROR && "PACK_TEMPLATE_INVALID".equals(i.code()));
        assertThat(issues).noneMatch(i -> "error.pack.template_code_mismatch".equals(i.message()));
    }

    @Test
    void validateReservedSystemKeyIsError() throws Exception {
        byte[] reserved = readFixture("invalid-template-metadata-reserved-key.json");
        List<PackValidationIssue> issues = parser.validate("metadata/reserved.json", "ARTISAN_DEVIS", reserved);

        assertThat(issues).anyMatch(i ->
                "PACK_TEMPLATE_INVALID".equals(i.code())
                        && ("error.pack.template_reserved_variable".equals(i.message())
                                || "error.pack.template_invalid".equals(i.message())));
    }

    @Test
    void validateDuplicateVariableKeysIsError() {
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "DEMO_QUOTE",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1},
                    {"key": "client.name", "label": "Client 2", "type": "TEXT", "required": false, "order": 2}
                  ]
                }
                """;
        List<PackValidationIssue> issues =
                parser.validate("metadata/demo.json", "DEMO_QUOTE", metadata.getBytes(StandardCharsets.UTF_8));

        assertThat(issues).anyMatch(i ->
                "error.pack.template_variable_duplicated".equals(i.message())
                        && "client.name".equals(i.variable()));
    }

    @Test
    void validateCodeMismatchAndBlankExpectedSkipsMismatch() {
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "OTHER",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1}
                  ]
                }
                """;
        byte[] bytes = metadata.getBytes(StandardCharsets.UTF_8);

        assertThat(parser.validate("metadata/demo.json", "DEMO_QUOTE", bytes))
                .anyMatch(i -> "error.pack.template_code_mismatch".equals(i.message()));
        assertThat(parser.validate("metadata/demo.json", "  ", bytes))
                .noneMatch(i -> "error.pack.template_code_mismatch".equals(i.message()));
        assertThat(parser.validate("metadata/demo.json", null, bytes))
                .noneMatch(i -> "error.pack.template_code_mismatch".equals(i.message()));
    }

    @Test
    void validateMissingBytesYieldsIssue() {
        List<PackValidationIssue> issues = parser.validate("metadata/demo.json", "DEMO", null);
        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("PACK_TEMPLATE_INVALID");
            assertThat(i.message()).isEqualTo("error.pack.template_metadata_missing");
            assertThat(i.file()).isEqualTo("metadata/demo.json");
        });
    }

    @Test
    void parseAndToMetadataHappyPath() throws Exception {
        byte[] json = readFixture("valid-template-metadata.json");
        JsonNode tree = parser.readTree(json);
        PackTemplateMetadata viaTree = parser.toMetadata(tree);
        PackTemplateMetadata viaParse = parser.parse(json);

        assertThat(viaTree.code()).isEqualTo("ARTISAN_DEVIS");
        assertThat(viaParse.code()).isEqualTo(viaTree.code());
        assertThat(viaParse.variables()).isNotEmpty();
    }

    @Test
    void mapDbpfTypeTrimsAndMapsKnownTypes() {
        assertThat(PackTemplateMetadataParser.mapDbpfType(" integer ")).isEqualTo(VariableType.NUMBER);
        assertThat(PackTemplateMetadataParser.mapDbpfType("currency")).isEqualTo(VariableType.CURRENCY);
        assertThat(PackTemplateMetadataParser.mapDbpfType("TEXT")).isEqualTo(VariableType.TEXT);
        assertThatThrownBy(() -> PackTemplateMetadataParser.mapDbpfType(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackTemplateMetadataParser.mapDbpfType("NOT_A_TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] readFixture(String name) throws Exception {
        try (InputStream in = PackTemplateMetadataParserTest.class.getResourceAsStream("/packs/dbpf/" + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }
}
