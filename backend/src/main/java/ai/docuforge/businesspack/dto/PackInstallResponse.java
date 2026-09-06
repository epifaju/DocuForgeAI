package ai.docuforge.businesspack.dto;

import ai.docuforge.domain.businesspack.PackInstallationStatus;
import ai.docuforge.domain.businesspack.PackInstallationType;
import java.util.UUID;

public record PackInstallResponse(
        UUID jobId,
        UUID packId,
        UUID packVersionId,
        UUID installationId,
        String packKey,
        String version,
        PackInstallationType installationType,
        PackInstallationStatus installationStatus,
        int templatesInstalled,
        int promptsInstalled
) {
}
