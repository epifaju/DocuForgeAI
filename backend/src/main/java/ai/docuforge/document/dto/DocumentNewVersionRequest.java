package ai.docuforge.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record DocumentNewVersionRequest(
        @Size(max = 255)
        String title,

        @NotNull
        Map<String, Object> data
) {
}
