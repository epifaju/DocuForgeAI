package ai.docuforge.form.dto;

import ai.docuforge.domain.template.VariableType;

public record FormFieldSchema(
        String key,
        String label,
        VariableType type,
        boolean required,
        String defaultValue,
        String placeholder,
        int displayOrder,
        String component,
        FormFieldConstraints validation,
        boolean aiEnabled,
        String aiMode
) {
}
