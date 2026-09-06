package ai.docuforge.businesspack.template;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.manifest.PackValidationSeverity;
import ai.docuforge.businesspack.schema.DbpfSchemaSupport;
import ai.docuforge.domain.template.VariableType;
import ai.docuforge.template.parser.DocxVariableParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackTemplateValidatorTest {

    private PackTemplateValidator validator;
    private PackTemplateMetadataParser metadataParser;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        metadataParser = new PackTemplateMetadataParser(new DbpfSchemaSupport(mapper));
        validator = new PackTemplateValidator(new DocxVariableParser(), metadataParser);
    }

    @Test
    void matchingDocxAndMetadataPassesWithOnlyUnusedWarningsAbsentWhenAllUsed() throws Exception {
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "DEMO_QUOTE",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1},
                    {"key": "quote.amount", "label": "Amount", "type": "CURRENCY", "required": true, "order": 2}
                  ]
                }
                """;
        byte[] docx = docxWithText("Client {{client.name}} — {{quote.amount}}");

        List<PackValidationIssue> issues = validator.validate(
                "DEMO_QUOTE",
                "templates/demo.docx",
                docx,
                "metadata/demo.json",
                metadata.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(issues).noneMatch(i -> i.severity() == PackValidationSeverity.ERROR);
        assertThat(issues).noneMatch(i -> "PACK_TEMPLATE_UNUSED_VARIABLE".equals(i.code()));
        assertThat(issues).noneMatch(i -> "PACK_TEMPLATE_UNDECLARED_VARIABLE".equals(i.code()));
    }

    @Test
    void undeclaredPlaceholderIsError() throws Exception {
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "DEMO_QUOTE",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1}
                  ]
                }
                """;
        byte[] docx = docxWithText("{{client.name}} {{client.email}}");

        List<PackValidationIssue> issues = validator.validate(
                "DEMO_QUOTE",
                "templates/demo.docx",
                docx,
                "metadata/demo.json",
                metadata.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(issues).anyMatch(i ->
                "PACK_TEMPLATE_UNDECLARED_VARIABLE".equals(i.code())
                        && "client.email".equals(i.variable()));
    }

    @Test
    void unusedMetadataVariableIsWarning() throws Exception {
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "DEMO_QUOTE",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1},
                    {"key": "client.phone", "label": "Phone", "type": "PHONE", "required": false, "order": 2}
                  ]
                }
                """;
        byte[] docx = docxWithText("Hello {{client.name}}");

        List<PackValidationIssue> issues = validator.validate(
                "DEMO_QUOTE",
                "templates/demo.docx",
                docx,
                "metadata/demo.json",
                metadata.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(issues).anyMatch(i ->
                i.severity() == PackValidationSeverity.WARNING
                        && "PACK_TEMPLATE_UNUSED_VARIABLE".equals(i.code())
                        && "client.phone".equals(i.variable()));
        assertThat(issues).noneMatch(i -> i.severity() == PackValidationSeverity.ERROR);
    }

    @Test
    void systemPlaceholderDoesNotRequireMetadata() throws Exception {
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "DEMO_QUOTE",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1}
                  ]
                }
                """;
        byte[] docx = docxWithText("{{client.name}} generated {{system.generationDate}}");

        List<PackValidationIssue> issues = validator.validate(
                "DEMO_QUOTE",
                "templates/demo.docx",
                docx,
                "metadata/demo.json",
                metadata.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(issues).noneMatch(i -> "PACK_TEMPLATE_UNDECLARED_VARIABLE".equals(i.code()));
        assertThat(issues).noneMatch(i -> i.severity() == PackValidationSeverity.ERROR);
    }

    @Test
    void corruptDocxIsInvalid() {
        byte[] bogus = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00, 0x01};
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "DEMO_QUOTE",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1}
                  ]
                }
                """;

        List<PackValidationIssue> issues = validator.validate(
                "DEMO_QUOTE",
                "templates/demo.docx",
                bogus,
                "metadata/demo.json",
                metadata.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(issues).anyMatch(i -> "PACK_TEMPLATE_INVALID".equals(i.code())
                && i.severity() == PackValidationSeverity.ERROR);
    }

    @Test
    void validClasspathMetadataFixtureParsesAndMapsInteger() throws IOException {
        byte[] json = readFixture("valid-template-metadata.json");
        List<PackValidationIssue> schemaIssues =
                metadataParser.validate("metadata/artisan-devis.json", "ARTISAN_DEVIS", json);
        assertThat(schemaIssues).isEmpty();

        PackTemplateMetadata metadata = metadataParser.parse(json);
        assertThat(metadata.code()).isEqualTo("ARTISAN_DEVIS");
        assertThat(PackTemplateMetadataParser.mapDbpfType("INTEGER")).isEqualTo(VariableType.NUMBER);
        assertThat(PackTemplateMetadataParser.mapDbpfType("CURRENCY")).isEqualTo(VariableType.CURRENCY);
    }

    @Test
    void metadataCodeMismatchIsError() throws Exception {
        String metadata = """
                {
                  "schemaVersion": "DBPF-TEMPLATE-1",
                  "code": "OTHER_CODE",
                  "name": "Demo Quote",
                  "version": "1.0.0",
                  "outputFormats": ["DOCX"],
                  "variables": [
                    {"key": "client.name", "label": "Client", "type": "TEXT", "required": true, "order": 1}
                  ]
                }
                """;
        byte[] docx = docxWithText("{{client.name}}");

        List<PackValidationIssue> issues = validator.validate(
                "DEMO_QUOTE",
                "templates/demo.docx",
                docx,
                "metadata/demo.json",
                metadata.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(issues).anyMatch(i ->
                "PACK_TEMPLATE_INVALID".equals(i.code())
                        && "error.pack.template_code_mismatch".equals(i.message()));
    }

    private static byte[] docxWithText(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] readFixture(String name) throws IOException {
        try (InputStream in = PackTemplateValidatorTest.class.getResourceAsStream("/packs/dbpf/" + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }
}
