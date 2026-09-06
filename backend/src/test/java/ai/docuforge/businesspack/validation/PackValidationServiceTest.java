package ai.docuforge.businesspack.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.businesspack.archive.PackArchiveContentReader;
import ai.docuforge.businesspack.archive.PackArchiveInspector;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.businesspack.compatibility.PackCompatibilityService;
import ai.docuforge.businesspack.manifest.PackManifestValidator;
import ai.docuforge.businesspack.manifest.PackManifestParser;
import ai.docuforge.businesspack.manifest.PackValidationSeverity;
import ai.docuforge.businesspack.schema.DbpfSchemaSupport;
import ai.docuforge.businesspack.template.PackTemplateMetadataParser;
import ai.docuforge.businesspack.template.PackTemplateValidator;
import ai.docuforge.config.PackProperties;
import ai.docuforge.security.antivirus.NoOpAntivirusScanner;
import ai.docuforge.template.parser.DocxVariableParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackValidationServiceTest {

    @TempDir
    Path tempDir;

    private PackValidationService service;
    private PackChecksumValidator checksumValidator;
    private PackArchiveContentReader contentReader;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        PackProperties props = new PackProperties(
                true, 10, 50, 100, 20, 10, 50, 24, "0.1.0", Set.of("json", "docx", "txt", "png", "md", "csv")
        );
        checksumValidator = new PackChecksumValidator();
        contentReader = new PackArchiveContentReader();
        DbpfSchemaSupport schemas = new DbpfSchemaSupport(mapper);
        service = new PackValidationService(
                new PackArchiveInspector(props),
                contentReader,
                new PackManifestValidator(new PackManifestParser(mapper), schemas),
                new PackCompatibilityService(props),
                checksumValidator,
                new PackTemplateValidator(new DocxVariableParser(), new PackTemplateMetadataParser(schemas)),
                new NoOpAntivirusScanner(),
                mapper
        );
    }

    @Test
    void validatesCompleteMinimalPack() throws Exception {
        byte[] docx = docxWithText("Hello {{client.name}}");
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
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);
        byte[] promptBytes = "Rewrite professionally.".getBytes(StandardCharsets.UTF_8);
        byte[] sampleBytes = "{\"client\":{\"name\":\"ACME Demo\"}}".getBytes(StandardCharsets.UTF_8);

        String manifest = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.demo",
                  "name": "Demo Pack",
                  "slug": "demo",
                  "version": "1.0.0",
                  "type": "CUSTOM",
                  "description": "Minimal validation fixture",
                  "publisher": { "id": "docuforge", "name": "DocuForge AI" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "DEMO_QUOTE",
                      "name": "Demo Quote",
                      "version": "1.0.0",
                      "templateFile": "templates/demo.docx",
                      "metadataFile": "metadata/demo.json"
                    }
                  ],
                  "prompts": [
                    { "code": "DEMO_PROMPT", "version": "1.0.0", "file": "prompts/demo.txt" }
                  ],
                  "samples": [
                    { "code": "DEMO_SAMPLE", "type": "JSON", "file": "samples/demo.json", "templateCode": "DEMO_QUOTE" }
                  ],
                  "checksums": {
                    "templates/demo.docx": "%s",
                    "metadata/demo.json": "%s",
                    "prompts/demo.txt": "%s",
                    "samples/demo.json": "%s"
                  }
                }
                """.formatted(
                checksumValidator.digestPrefixed(docx),
                checksumValidator.digestPrefixed(metadataBytes),
                checksumValidator.digestPrefixed(promptBytes),
                checksumValidator.digestPrefixed(sampleBytes)
        );

        Path zip = tempDir.resolve("valid.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            put(out, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            put(out, "templates/demo.docx", docx);
            put(out, "metadata/demo.json", metadataBytes);
            put(out, "prompts/demo.txt", promptBytes);
            put(out, "samples/demo.json", sampleBytes);
        }

        PackValidationReport report = service.validate(zip);
        assertThat(report.valid()).isTrue();
        assertThat(report.pack().id()).isEqualTo("com.docuforge.pack.demo");
        assertThat(report.summary().templates()).isEqualTo(1);
        assertThat(report.summary().prompts()).isEqualTo(1);
        assertThat(report.summary().errors()).isZero();
        assertThat(report.issues()).noneMatch(i -> i.severity() == PackValidationSeverity.ERROR);
    }

    @Test
    void acceptsSingleTopLevelDirectoryRoot() throws Exception {
        byte[] docx = docxWithText("{{client.name}}");
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
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);
        String manifest = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.nested",
                  "name": "Nested",
                  "slug": "nested",
                  "version": "1.0.0",
                  "type": "CUSTOM",
                  "description": "Nested root fixture",
                  "publisher": { "id": "docuforge", "name": "DocuForge AI" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "DEMO_QUOTE",
                      "name": "Demo Quote",
                      "version": "1.0.0",
                      "templateFile": "templates/demo.docx",
                      "metadataFile": "metadata/demo.json"
                    }
                  ],
                  "checksums": {
                    "templates/demo.docx": "%s",
                    "metadata/demo.json": "%s"
                  }
                }
                """.formatted(
                checksumValidator.digestPrefixed(docx),
                checksumValidator.digestPrefixed(metadataBytes)
        );

        Path zip = tempDir.resolve("nested.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            put(out, "pack-root/manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            put(out, "pack-root/templates/demo.docx", docx);
            put(out, "pack-root/metadata/demo.json", metadataBytes);
        }

        PackValidationReport report = service.validate(zip);
        assertThat(report.valid()).isTrue();
        assertThat(report.pack().id()).isEqualTo("com.docuforge.pack.nested");
    }

    @Test
    void missingReferencedFileFails() throws Exception {
        String manifest = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.missing",
                  "name": "Missing",
                  "slug": "missing",
                  "version": "1.0.0",
                  "type": "CUSTOM",
                  "description": "Missing file fixture",
                  "publisher": { "id": "docuforge", "name": "DocuForge AI" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "DEMO_QUOTE",
                      "name": "Demo Quote",
                      "version": "1.0.0",
                      "templateFile": "templates/demo.docx",
                      "metadataFile": "metadata/demo.json"
                    }
                  ],
                  "checksums": {
                    "templates/demo.docx": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "metadata/demo.json": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                  }
                }
                """;
        Path zip = tempDir.resolve("missing.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            put(out, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
        }

        PackValidationReport report = service.validate(zip);
        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).anyMatch(i -> "PACK_FILE_MISSING".equals(i.code()));
    }

    @Test
    void checksumMismatchFails() throws Exception {
        byte[] docx = docxWithText("{{client.name}}");
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
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);
        String manifest = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.badsum",
                  "name": "Badsum",
                  "slug": "badsum",
                  "version": "1.0.0",
                  "type": "CUSTOM",
                  "description": "Checksum mismatch fixture",
                  "publisher": { "id": "docuforge", "name": "DocuForge AI" },
                  "compatibility": { "minimumDocuForgeVersion": "0.1.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "DEMO_QUOTE",
                      "name": "Demo Quote",
                      "version": "1.0.0",
                      "templateFile": "templates/demo.docx",
                      "metadataFile": "metadata/demo.json"
                    }
                  ],
                  "checksums": {
                    "templates/demo.docx": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "metadata/demo.json": "%s"
                  }
                }
                """.formatted(checksumValidator.digestPrefixed(metadataBytes));

        Path zip = tempDir.resolve("badsum.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            put(out, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            put(out, "templates/demo.docx", docx);
            put(out, "metadata/demo.json", metadataBytes);
        }

        PackValidationReport report = service.validate(zip);
        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).anyMatch(i -> "PACK_CHECKSUM_MISMATCH".equals(i.code()));
    }

    @Test
    void incompatiblePlatformVersionFails() throws Exception {
        byte[] docx = docxWithText("{{client.name}}");
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
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);
        String manifest = """
                {
                  "schemaVersion": "DBPF-1",
                  "id": "com.docuforge.pack.future",
                  "name": "Future",
                  "slug": "future",
                  "version": "1.0.0",
                  "type": "CUSTOM",
                  "description": "Incompatible platform fixture",
                  "publisher": { "id": "docuforge", "name": "DocuForge AI" },
                  "compatibility": { "minimumDocuForgeVersion": "9.0.0" },
                  "locales": ["fr-FR"],
                  "defaultLocale": "fr-FR",
                  "templates": [
                    {
                      "code": "DEMO_QUOTE",
                      "name": "Demo Quote",
                      "version": "1.0.0",
                      "templateFile": "templates/demo.docx",
                      "metadataFile": "metadata/demo.json"
                    }
                  ],
                  "checksums": {
                    "templates/demo.docx": "%s",
                    "metadata/demo.json": "%s"
                  }
                }
                """.formatted(
                checksumValidator.digestPrefixed(docx),
                checksumValidator.digestPrefixed(metadataBytes)
        );

        Path zip = tempDir.resolve("incompat.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            put(out, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            put(out, "templates/demo.docx", docx);
            put(out, "metadata/demo.json", metadataBytes);
        }

        PackValidationReport report = service.validate(zip);
        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).anyMatch(i -> "PACK_INCOMPATIBLE_DOCUFORGE_VERSION".equals(i.code()));
    }

    @Test
    void resolveRootPrefixDetectsNestedAndFlat() {
        assertThat(contentReader.resolveRootPrefix(List.of("manifest.json", "templates/a.docx")))
                .contains("");
        assertThat(contentReader.resolveRootPrefix(List.of("pack/manifest.json", "pack/templates/a.docx")))
                .contains("pack/");
        assertThat(contentReader.resolveRootPrefix(List.of("a/manifest.json", "b/manifest.json")))
                .isEmpty();
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

    private static void put(ZipArchiveOutputStream out, String name, byte[] data) throws Exception {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        out.putArchiveEntry(entry);
        out.write(data);
        out.closeArchiveEntry();
    }
}
