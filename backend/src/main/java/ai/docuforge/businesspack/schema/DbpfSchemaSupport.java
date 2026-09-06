package ai.docuforge.businesspack.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;

/**
 * Shared classpath loader for DBPF JSON Schemas (Phase 1 artifacts).
 */
@Component
public class DbpfSchemaSupport {

    public static final String MANIFEST_SCHEMA_PATH = "/schemas/docuforge-business-pack-v1.schema.json";
    public static final String TEMPLATE_METADATA_SCHEMA_PATH = "/schemas/docuforge-template-metadata-v1.schema.json";

    private final ObjectMapper objectMapper;
    private final JsonSchema manifestSchema;
    private final JsonSchema templateMetadataSchema;

    public DbpfSchemaSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();
            this.manifestSchema = factory.getSchema(readTree(MANIFEST_SCHEMA_PATH), config);
            this.templateMetadataSchema = factory.getSchema(readTree(TEMPLATE_METADATA_SCHEMA_PATH), config);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load DBPF JSON Schemas from classpath", ex);
        }
    }

    public JsonSchema manifestSchema() {
        return manifestSchema;
    }

    public JsonSchema templateMetadataSchema() {
        return templateMetadataSchema;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    private JsonNode readTree(String classpath) throws IOException {
        try (InputStream in = DbpfSchemaSupport.class.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IOException("Missing classpath resource: " + classpath);
            }
            return objectMapper.readTree(in);
        }
    }
}
