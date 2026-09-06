package ai.docuforge.businesspack.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 1 — validates DBPF-1 JSON Schema artifacts and fixtures (no DB, no install).
 */
class DbpfJsonSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MANIFEST_SCHEMA = "/schemas/docuforge-business-pack-v1.schema.json";
    private static final String TEMPLATE_SCHEMA = "/schemas/docuforge-template-metadata-v1.schema.json";

    private static JsonSchema manifestSchema;
    private static JsonSchema templateMetadataSchema;

    @BeforeAll
    static void loadSchemas() throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();
        manifestSchema = factory.getSchema(readTree(MANIFEST_SCHEMA), config);
        templateMetadataSchema = factory.getSchema(readTree(TEMPLATE_SCHEMA), config);
    }

    @Test
    void validManifestFixturePassesSchema() throws IOException {
        Set<ValidationMessage> errors = manifestSchema.validate(readFixture("valid-manifest.json"));
        assertThat(errors).isEmpty();
    }

    @Test
    void validTemplateMetadataFixturePassesSchema() throws IOException {
        Set<ValidationMessage> errors = templateMetadataSchema.validate(readFixture("valid-template-metadata.json"));
        assertThat(errors).isEmpty();
    }

    @Test
    void manifestMissingIdFailsSchema() throws IOException {
        Set<ValidationMessage> errors = manifestSchema.validate(readFixture("invalid-manifest-missing-id.json"));
        assertThat(errors).isNotEmpty();
        assertThat(joined(errors)).containsIgnoringCase("id");
    }

    @Test
    void manifestBadSlugFailsSchema() throws IOException {
        Set<ValidationMessage> errors = manifestSchema.validate(readFixture("invalid-manifest-bad-slug.json"));
        assertThat(errors).isNotEmpty();
        assertThat(joined(errors)).containsIgnoringCase("slug");
    }

    @Test
    void templateMetadataBadKeyFailsSchema() throws IOException {
        Set<ValidationMessage> errors =
                templateMetadataSchema.validate(readFixture("invalid-template-metadata-bad-key.json"));
        assertThat(errors).isNotEmpty();
        assertThat(joined(errors)).containsAnyOf("key", "pattern");
    }

    @Test
    void templateMetadataUnsupportedTypeFailsSchema() throws IOException {
        Set<ValidationMessage> errors =
                templateMetadataSchema.validate(readFixture("invalid-template-metadata-bad-type.json"));
        assertThat(errors).isNotEmpty();
        assertThat(joined(errors)).containsAnyOf("type", "enum", "IMAGE");
    }

    @Test
    void templateMetadataReservedSystemKeyFailsSchema() throws IOException {
        Set<ValidationMessage> errors =
                templateMetadataSchema.validate(readFixture("invalid-template-metadata-reserved-key.json"));
        assertThat(errors).isNotEmpty();
    }

    @Test
    void schemasAreLoadableFromClasspath() {
        assertThat(DbpfJsonSchemaTest.class.getResource(MANIFEST_SCHEMA)).isNotNull();
        assertThat(DbpfJsonSchemaTest.class.getResource(TEMPLATE_SCHEMA)).isNotNull();
    }

    private static JsonNode readFixture(String name) throws IOException {
        return readTree("/packs/dbpf/" + name);
    }

    private static JsonNode readTree(String classpath) throws IOException {
        try (InputStream in = DbpfJsonSchemaTest.class.getResourceAsStream(classpath)) {
            assertThat(in).as("Missing classpath resource %s", classpath).isNotNull();
            return MAPPER.readTree(in);
        }
    }

    private static String joined(Set<ValidationMessage> errors) {
        return errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining(" | "));
    }
}
