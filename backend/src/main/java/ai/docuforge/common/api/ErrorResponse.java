package ai.docuforge.common.api;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldErrorDetail> details,
        String traceId
) {
    public record FieldErrorDetail(String field, String message) {
    }
}