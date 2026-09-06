package ai.docuforge.businesspack.dto;

import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.BusinessPackType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pack detail for ADMIN (PRD §84).
 */
public record PackDetailResponse(
        UUID id,
        String packKey,
        String slug,
        String name,
        String description,
        BusinessPackType packType,
        String publisherId,
        String publisherName,
        BusinessPackStatus status,
        PackVersionResponse currentVersion,
        List<PackVersionResponse> versions,
        List<PackTemplateResponse> templates,
        List<PackPromptResponse> prompts,
        PackInstallationResponse installation,
        Instant createdAt,
        Instant updatedAt
) {
}
