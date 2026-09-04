package ai.docuforge.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        String entityType,
        String entityId,
        String status,
        String ipAddress,
        String metadata,
        UUID userId,
        String userEmail,
        Instant createdAt
) {
}
