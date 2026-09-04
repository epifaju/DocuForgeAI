package ai.docuforge.ai.dto;

import jakarta.validation.constraints.Size;

public record AiAssistRequest(
        @Size(max = 20000)
        String text,

        @Size(max = 2000)
        String instruction,

        @Size(max = 4000)
        String context
) {
}
