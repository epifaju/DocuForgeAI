package ai.docuforge.template.dto;

import ai.docuforge.domain.template.VariableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TemplateVariableUpdateItem(
        @NotBlank
        @Size(max = 200)
        String key,

        @NotBlank
        @Size(max = 200)
        String label,

        @NotNull
        VariableType type,

        boolean required,

        String defaultValue,

        String placeholder,

        Integer displayOrder,

        String configuration
) {
}