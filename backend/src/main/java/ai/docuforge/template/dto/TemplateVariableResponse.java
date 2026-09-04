package ai.docuforge.template.dto;

import ai.docuforge.domain.template.VariableType;
import java.time.Instant;
import java.util.UUID;

public record TemplateVariableResponse(
        UUID id,
        String key,
        String label,
        VariableType type,
        boolean required,
        String defaultValue,
        String placeholder,
        int displayOrder,
        String configuration,
        Instant createdAt
) {
}