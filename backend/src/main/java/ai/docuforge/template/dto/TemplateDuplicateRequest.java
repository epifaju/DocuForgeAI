package ai.docuforge.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Duplicate a template into an independent USER copy (PRD §§125–127).
 */
public record TemplateDuplicateRequest(
        @NotBlank
        @Size(max = 200)
        String name
) {
}
