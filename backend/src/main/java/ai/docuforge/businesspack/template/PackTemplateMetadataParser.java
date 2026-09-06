package ai.docuforge.businesspack.template;

import ai.docuforge.businesspack.manifest.PackManifestException;
import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.manifest.PackValidationSeverity;
import ai.docuforge.businesspack.schema.DbpfSchemaSupport;
import ai.docuforge.domain.template.VariableType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Parses and schema-validates DBPF-TEMPLATE-1 metadata JSON (Phase 7).
 */
@Component
public class PackTemplateMetadataParser {

    private final DbpfSchemaSupport schemas;

    public PackTemplateMetadataParser(DbpfSchemaSupport schemas) {
        this.schemas = schemas;
    }

    public JsonNode readTree(byte[] utf8Json) {
        if (utf8Json == null || utf8Json.length == 0) {
            throw missing();
        }
        return readTree(new String(utf8Json, StandardCharsets.UTF_8));
    }

    public JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            throw missing();
        }
        try {
            JsonNode node = schemas.objectMapper().readTree(json);
            if (node == null || node.isNull() || !node.isObject()) {
                throw invalidJson(null);
            }
            return node;
        } catch (PackManifestException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw invalidJson(ex);
        } catch (Exception ex) {
            throw invalidJson(ex);
        }
    }

    public PackTemplateMetadata toMetadata(JsonNode tree) {
        try {
            return schemas.objectMapper().treeToValue(tree, PackTemplateMetadata.class);
        } catch (JsonProcessingException ex) {
            throw new PackManifestException(
                    "PACK_TEMPLATE_INVALID",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "error.pack.template_invalid",
                    ex
            );
        }
    }

    /**
     * Schema validation + light semantic checks (duplicate keys, reserved {@code system.*}, code match).
     */
    public List<PackValidationIssue> validate(String metadataLogicalPath, String expectedTemplateCode, byte[] utf8Json) {
        List<PackValidationIssue> issues = new ArrayList<>();
        JsonNode tree;
        try {
            tree = readTree(utf8Json);
        } catch (PackManifestException ex) {
            issues.add(PackValidationIssue.error(ex.getCode(), ex.getMessage()).withFile(metadataLogicalPath));
            return List.copyOf(issues);
        }

        Set<ValidationMessage> schemaErrors = schemas.templateMetadataSchema().validate(tree);
        for (ValidationMessage ignored : schemaErrors) {
            issues.add(PackValidationIssue.error("PACK_TEMPLATE_INVALID", "error.pack.template_invalid")
                    .withFile(metadataLogicalPath));
        }

        boolean blocking = issues.stream().anyMatch(i -> i.severity() == PackValidationSeverity.ERROR);
        if (blocking) {
            return List.copyOf(issues);
        }

        PackTemplateMetadata metadata = toMetadata(tree);
        if (StringUtils.hasText(expectedTemplateCode)
                && StringUtils.hasText(metadata.code())
                && !expectedTemplateCode.equals(metadata.code())) {
            issues.add(PackValidationIssue.error("PACK_TEMPLATE_INVALID", "error.pack.template_code_mismatch")
                    .withFile(metadataLogicalPath)
                    .withTemplateCode(expectedTemplateCode));
        }

        if (metadata.variables() != null) {
            Set<String> keys = new HashSet<>();
            for (PackTemplateMetadata.Variable variable : metadata.variables()) {
                if (variable == null || !StringUtils.hasText(variable.key())) {
                    continue;
                }
                String key = variable.key().trim();
                if (key.toLowerCase(Locale.ROOT).startsWith("system.")) {
                    issues.add(PackValidationIssue.error("PACK_TEMPLATE_INVALID", "error.pack.template_reserved_variable")
                            .withFile(metadataLogicalPath)
                            .withTemplateCode(expectedTemplateCode)
                            .withVariable(key));
                }
                if (!keys.add(key)) {
                    issues.add(PackValidationIssue.error("PACK_TEMPLATE_INVALID", "error.pack.template_variable_duplicated")
                            .withFile(metadataLogicalPath)
                            .withTemplateCode(expectedTemplateCode)
                            .withVariable(key));
                }
                if (StringUtils.hasText(variable.type())) {
                    try {
                        mapDbpfType(variable.type());
                    } catch (IllegalArgumentException ex) {
                        issues.add(PackValidationIssue.error("PACK_TEMPLATE_INVALID", "error.pack.template_invalid")
                                .withFile(metadataLogicalPath)
                                .withVariable(key));
                    }
                }
            }
        }

        return List.copyOf(issues);
    }

    public PackTemplateMetadata parse(byte[] utf8Json) {
        return toMetadata(readTree(utf8Json));
    }

    /**
     * Maps DBPF-1 variable types to DocuForge {@link VariableType} ({@code INTEGER} → {@code NUMBER}).
     */
    public static VariableType mapDbpfType(String dbpfType) {
        if (!StringUtils.hasText(dbpfType)) {
            throw new IllegalArgumentException("type required");
        }
        String type = dbpfType.trim().toUpperCase(Locale.ROOT);
        if ("INTEGER".equals(type)) {
            return VariableType.NUMBER;
        }
        return VariableType.valueOf(type);
    }

    private static PackManifestException missing() {
        return new PackManifestException(
                "PACK_TEMPLATE_INVALID",
                HttpStatus.BAD_REQUEST,
                "error.pack.template_metadata_missing"
        );
    }

    private static PackManifestException invalidJson(Throwable cause) {
        return new PackManifestException(
                "PACK_TEMPLATE_INVALID",
                HttpStatus.BAD_REQUEST,
                "error.pack.template_invalid",
                cause
        );
    }
}
