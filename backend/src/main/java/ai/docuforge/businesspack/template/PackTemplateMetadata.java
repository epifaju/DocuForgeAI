package ai.docuforge.businesspack.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DBPF-TEMPLATE-1 metadata DTO (Jackson).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PackTemplateMetadata(
        String schemaVersion,
        String code,
        String name,
        String description,
        String category,
        String version,
        List<String> outputFormats,
        List<Variable> variables
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variable(
            String key,
            String label,
            String type,
            Boolean required,
            Integer order,
            Object defaultValue,
            String placeholder,
            List<SelectOption> options,
            Validation validation,
            AiAssist ai
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelectOption(String value, String label) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Validation(
            Integer minLength,
            Integer maxLength,
            String pattern,
            Number minimum,
            Number maximum
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiAssist(Boolean enabled, List<String> operations, String promptCode) {
    }
}
