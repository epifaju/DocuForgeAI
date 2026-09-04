package ai.docuforge.form;

import ai.docuforge.domain.template.VariableType;
import ai.docuforge.form.dto.FormFieldConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FormFieldConstraintsParser {

    private final ObjectMapper objectMapper;

    public FormFieldConstraintsParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FormFieldConstraints parse(String configurationJson) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return new FormFieldConstraints(null, null, null, null, null, List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(configurationJson);
            JsonNode validation = root.has("validation") ? root.get("validation") : root;
            Integer minLength = intOrNull(validation, "minLength");
            Integer maxLength = intOrNull(validation, "maxLength");
            BigDecimal min = decimalOrNull(validation, "min");
            BigDecimal max = decimalOrNull(validation, "max");
            String pattern = textOrNull(validation, "pattern");
            List<String> options = options(root, validation);
            return new FormFieldConstraints(minLength, maxLength, min, max, pattern, options);
        } catch (Exception ex) {
            return new FormFieldConstraints(null, null, null, null, null, List.of());
        }
    }

    /** LONG_TEXT is AI-assistable by default (PRD §36); configuration can override. */
    public boolean aiEnabled(String configurationJson, VariableType type) {
        Boolean configured = boolOrNull(configurationJson, "aiEnabled");
        if (configured != null) {
            return configured;
        }
        return type == VariableType.LONG_TEXT || type == VariableType.TEXT;
    }

    public String aiMode(String configurationJson, VariableType type) {
        String configured = textFromRoot(configurationJson, "aiMode");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return type == VariableType.LONG_TEXT ? "GENERATE_PARAGRAPH" : "REWRITE";
    }

    private Boolean boolOrNull(String configurationJson, String field) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(configurationJson);
            if (!root.has(field) || root.get(field).isNull()) {
                return null;
            }
            return root.get(field).asBoolean();
        } catch (Exception ex) {
            return null;
        }
    }

    private String textFromRoot(String configurationJson, String field) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(configurationJson);
            return textOrNull(root, field);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return new BigDecimal(node.get(field).asText());
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static List<String> options(JsonNode root, JsonNode validation) {
        JsonNode optionsNode = null;
        if (root.has("options")) {
            optionsNode = root.get("options");
        } else if (validation != null && validation.has("options")) {
            optionsNode = validation.get("options");
        }
        if (optionsNode == null || !optionsNode.isArray()) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        optionsNode.forEach(n -> {
            if (n.isTextual()) {
                options.add(n.asText());
            } else if (n.has("value")) {
                options.add(n.get("value").asText());
            }
        });
        return List.copyOf(options);
    }
}