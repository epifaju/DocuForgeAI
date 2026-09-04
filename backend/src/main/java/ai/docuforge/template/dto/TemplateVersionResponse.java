package ai.docuforge.template.dto;

import java.time.Instant;
import java.util.UUID;

public record TemplateVersionResponse(
        UUID id,
        int versionNumber,
        String originalFilename,
        String storageKey,
        String checksum,
        UUID createdBy,
        Instant createdAt
) {
}