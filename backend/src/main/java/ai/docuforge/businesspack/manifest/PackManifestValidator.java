package ai.docuforge.businesspack.manifest;

import ai.docuforge.businesspack.schema.DbpfSchemaSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JSON Schema + semantic validation for DBPF-1 manifests (PRD §64–§65 partial).
 * Checksums cryptographic verify and DocuForge SemVer compatibility are Phase 6+.
 * ZIP file existence is Phase 8.
 */
@Component
public class PackManifestValidator {

    private static final Set<String> KNOWN_TOP_LEVEL = Set.of(
            "schemaVersion",
            "id",
            "name",
            "slug",
            "version",
            "type",
            "description",
            "publisher",
            "compatibility",
            "locales",
            "defaultLocale",
            "categories",
            "tags",
            "templates",
            "prompts",
            "samples",
            "checksums"
    );

    private final PackManifestParser parser;
    private final DbpfSchemaSupport schemas;

    public PackManifestValidator(PackManifestParser parser, DbpfSchemaSupport schemas) {
        this.parser = parser;
        this.schemas = schemas;
    }

    public PackManifestValidationResult validate(String json) {
        JsonNode tree = parser.readTree(json);
        return validateTree(tree);
    }

    public PackManifestValidationResult validate(byte[] utf8Json) {
        JsonNode tree = parser.readTree(utf8Json);
        return validateTree(tree);
    }

    public PackManifestValidationResult validateTree(JsonNode tree) {
        List<PackValidationIssue> issues = new ArrayList<>();

        JsonNode schemaVersionNode = tree.get("schemaVersion");
        if (schemaVersionNode != null
                && schemaVersionNode.isTextual()
                && !"DBPF-1".equals(schemaVersionNode.asText())) {
            issues.add(PackValidationIssue.error(
                    "PACK_SCHEMA_UNSUPPORTED",
                    "error.pack.schema_unsupported"
            ));
        }

        Set<ValidationMessage> schemaErrors = schemas.manifestSchema().validate(tree);
        for (ValidationMessage message : schemaErrors) {
            issues.add(mapSchemaIssue(message));
        }

        collectUnknownTopLevelFields(tree, issues);

        boolean schemaBlocking = issues.stream().anyMatch(i -> i.severity() == PackValidationSeverity.ERROR);
        PackManifest manifest = null;
        if (!schemaBlocking) {
            manifest = parser.toManifest(tree);
            validateSemantics(manifest, issues);
        }

        int errors = (int) issues.stream().filter(i -> i.severity() == PackValidationSeverity.ERROR).count();
        int warnings = (int) issues.stream().filter(i -> i.severity() == PackValidationSeverity.WARNING).count();
        int templates = manifest == null || manifest.templates() == null ? 0 : manifest.templates().size();
        int prompts = manifest == null || manifest.prompts() == null ? 0 : manifest.prompts().size();

        return new PackManifestValidationResult(
                errors == 0,
                manifest,
                new PackManifestValidationResult.PackManifestSummary(errors, warnings, templates, prompts),
                List.copyOf(issues)
        );
    }

    private static PackValidationIssue mapSchemaIssue(ValidationMessage message) {
        String path = message.getInstanceLocation() == null ? "" : message.getInstanceLocation().toString();
        String lowerPath = path.toLowerCase();
        String lowerMsg = message.getMessage() == null ? "" : message.getMessage().toLowerCase();

        if (lowerPath.contains("schemaversion") || lowerMsg.contains("schemaversion")) {
            return PackValidationIssue.error("PACK_SCHEMA_UNSUPPORTED", "error.pack.schema_unsupported");
        }
        if (lowerPath.endsWith("/id") || lowerPath.contains("properties/id") || "$.id".equals(path)) {
            return PackValidationIssue.error("PACK_ID_INVALID", "error.pack.id_invalid");
        }
        if (lowerPath.contains("version") && !lowerPath.contains("schemaversion") && !lowerPath.contains("docuforge")) {
            return PackValidationIssue.error("PACK_VERSION_INVALID", "error.pack.version_invalid");
        }
        if (lowerPath.contains("locale")) {
            return PackValidationIssue.error("PACK_LOCALE_INVALID", "error.pack.locale_invalid");
        }
        if (lowerMsg.contains("required") && lowerMsg.contains("id")) {
            return PackValidationIssue.error("PACK_ID_INVALID", "error.pack.id_invalid");
        }
        return PackValidationIssue.error("PACK_MANIFEST_SCHEMA_INVALID", "error.pack.manifest_schema_invalid");
    }

    private static void collectUnknownTopLevelFields(JsonNode tree, List<PackValidationIssue> issues) {
        Iterator<String> names = tree.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!KNOWN_TOP_LEVEL.contains(name)) {
                issues.add(PackValidationIssue.warning(
                        "PACK_MANIFEST_UNKNOWN_FIELD",
                        "error.pack.manifest_unknown_field",
                        name
                ));
            }
        }
    }

    private static void validateSemantics(PackManifest manifest, List<PackValidationIssue> issues) {
        if (manifest.locales() != null
                && StringUtils.hasText(manifest.defaultLocale())
                && !manifest.locales().contains(manifest.defaultLocale())) {
            issues.add(PackValidationIssue.error("PACK_LOCALE_INVALID", "error.pack.locale_invalid"));
        }

        Set<String> templateCodes = new HashSet<>();
        if (manifest.templates() != null) {
            for (PackManifest.TemplateEntry template : manifest.templates()) {
                if (template == null || !StringUtils.hasText(template.code())) {
                    continue;
                }
                if (!templateCodes.add(template.code())) {
                    issues.add(PackValidationIssue.error("PACK_TEMPLATE_CODE_DUPLICATED", "error.pack.template_code_duplicated")
                            .withTemplateCode(template.code()));
                }
                requireChecksum(manifest.checksums(), template.templateFile(), issues);
                requireChecksum(manifest.checksums(), template.metadataFile(), issues);
                if (StringUtils.hasText(template.previewFile())) {
                    requireChecksum(manifest.checksums(), template.previewFile(), issues);
                }
            }
        }

        Set<String> promptCodes = new HashSet<>();
        if (manifest.prompts() != null) {
            for (PackManifest.PromptEntry prompt : manifest.prompts()) {
                if (prompt == null || !StringUtils.hasText(prompt.code())) {
                    continue;
                }
                if (!promptCodes.add(prompt.code())) {
                    issues.add(PackValidationIssue.error("PACK_PROMPT_DUPLICATED", "error.pack.prompt_duplicated"));
                }
                requireChecksum(manifest.checksums(), prompt.file(), issues);
            }
        }

        if (manifest.samples() != null) {
            for (PackManifest.SampleEntry sample : manifest.samples()) {
                if (sample == null) {
                    continue;
                }
                if (StringUtils.hasText(sample.templateCode()) && !templateCodes.contains(sample.templateCode())) {
                    issues.add(PackValidationIssue.error(
                                    "PACK_MANIFEST_SCHEMA_INVALID",
                                    "error.pack.sample_template_unknown")
                            .withTemplateCode(sample.templateCode()));
                }
                requireChecksum(manifest.checksums(), sample.file(), issues);
            }
        }
    }

    private static void requireChecksum(Map<String, String> checksums, String logicalPath, List<PackValidationIssue> issues) {
        if (!StringUtils.hasText(logicalPath)) {
            return;
        }
        if (checksums == null || !checksums.containsKey(logicalPath)) {
            issues.add(PackValidationIssue.warning(
                    "PACK_MANIFEST_CHECKSUM_UNDECLARED",
                    "error.pack.checksum_undeclared",
                    logicalPath
            ));
        }
    }
}
