package ai.docuforge.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TemplateVariablesUpdateRequest(
        @NotNull
        @Valid
        List<TemplateVariableUpdateItem> variables
) {
}