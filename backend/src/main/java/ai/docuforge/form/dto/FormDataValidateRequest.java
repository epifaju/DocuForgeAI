package ai.docuforge.form.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record FormDataValidateRequest(
        @NotNull
        Map<String, Object> data
) {
}