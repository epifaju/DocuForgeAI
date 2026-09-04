package ai.docuforge.batch.dto;

import ai.docuforge.domain.batch.BatchJobStatus;
import java.time.Instant;
import java.util.UUID;

public record BatchJobResponse(
        UUID id,
        BatchJobStatus status,
        UUID templateId,
        UUID templateVersionId,
        int totalItems,
        int processedItems,
        int successfulItems,
        int failedItems,
        boolean zipReady,
        boolean errorsReady,
        UUID createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
}
