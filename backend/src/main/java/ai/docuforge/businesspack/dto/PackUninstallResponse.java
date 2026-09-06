package ai.docuforge.businesspack.dto;

import ai.docuforge.domain.businesspack.BusinessPackStatus;
import java.util.UUID;

/**
 * Soft uninstall result (PRD §§94–96). Metadata and historical docs are always preserved.
 */
public record PackUninstallResponse(
        UUID packId,
        String packKey,
        BusinessPackStatus status,
        int templatesArchived,
        long historicalDocumentCount,
        boolean metadataPreserved
) {
}
