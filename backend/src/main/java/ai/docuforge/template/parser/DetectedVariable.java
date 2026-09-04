package ai.docuforge.template.parser;

import ai.docuforge.domain.template.VariableType;

public record DetectedVariable(
        String key,
        String label,
        VariableType type,
        int displayOrder
) {
}