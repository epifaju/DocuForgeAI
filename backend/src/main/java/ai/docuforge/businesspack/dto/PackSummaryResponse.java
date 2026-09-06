package ai.docuforge.businesspack.dto;

import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.BusinessPackType;
import java.time.Instant;
import java.util.UUID;

/**
 * Pack row for ADMIN list (PRD §83).
 */
public record PackSummaryResponse(
        UUID id,
        String packKey,
        String slug,
        String name,
        String description,
        BusinessPackType packType,
        String publisherId,
        String publisherName,
        BusinessPackStatus status,
        String currentVersion,
        UUID currentVersionId,
        Instant createdAt,
        Instant updatedAt
) {
}
