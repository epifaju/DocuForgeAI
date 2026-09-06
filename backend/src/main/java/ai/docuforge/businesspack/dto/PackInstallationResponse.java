package ai.docuforge.businesspack.dto;

import ai.docuforge.domain.businesspack.PackInstallationStatus;
import ai.docuforge.domain.businesspack.PackInstallationType;
import java.time.Instant;
import java.util.UUID;

/**
 * Latest installation snapshot for a pack in the caller's company (PRD §84).
 */
public record PackInstallationResponse(
        UUID id,
        UUID packVersionId,
        String packVersion,
        PackInstallationType installationType,
        PackInstallationStatus status,
        UUID installedBy,
        Instant installedAt,
        Instant disabledAt,
        Instant uninstalledAt
) {
}
