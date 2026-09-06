package ai.docuforge.businesspack.dto;

import ai.docuforge.domain.businesspack.PackVersionStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Pack version summary (PRD §§84–85). Manifest JSON omitted from list payloads.
 */
public record PackVersionResponse(
        UUID id,
        String version,
        String schemaVersion,
        String minimumDocuForgeVersion,
        String maximumDocuForgeVersion,
        String archiveChecksum,
        PackVersionStatus status,
        Instant installedAt,
        Instant createdAt,
        boolean current
) {
}
