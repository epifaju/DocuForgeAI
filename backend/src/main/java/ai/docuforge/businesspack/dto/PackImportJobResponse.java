package ai.docuforge.businesspack.dto;

import ai.docuforge.domain.businesspack.PackImportJobStatus;
import java.time.Instant;
import java.util.UUID;

public record PackImportJobResponse(
        UUID jobId,
        PackImportJobStatus status,
        String originalFilename,
        String detectedPackKey,
        String detectedVersion,
        Object validationReport,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant completedAt,
        Instant expiresAt
) {
}
