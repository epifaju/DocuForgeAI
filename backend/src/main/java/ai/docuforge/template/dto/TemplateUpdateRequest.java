package ai.docuforge.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TemplateUpdateRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_.-]{0,99}$", message = "Code template invalide")
        String code,

        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 5000)
        String description,

        @Size(max = 100)
        String category
) {
}