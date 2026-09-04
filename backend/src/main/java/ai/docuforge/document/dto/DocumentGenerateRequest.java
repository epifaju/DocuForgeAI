package ai.docuforge.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record DocumentGenerateRequest(
        @NotNull
        UUID templateId,

        UUID templateVersionId,

        @Size(max = 255)
        String title,

        @NotNull
        Map<String, Object> data
) {
}