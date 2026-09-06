package ai.docuforge.businesspack.dto;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.update.PackChangeItem;
import java.util.List;
import java.util.UUID;

/**
 * Update preview for a VALID import job against an installed pack (PRD §§90–93).
 */
public record PackUpdatePreviewResponse(
        boolean updateCandidate,
        boolean freshInstall,
        UUID packId,
        String packKey,
        String installedVersion,
        String candidateVersion,
        String updateKind,
        boolean semverBreakingMismatch,
        ChangeSummary summary,
        List<PackChangeItem> changes,
        List<PackValidationIssue> breakingChanges
) {
    public record ChangeSummary(
            int templatesAdded,
            int templatesUpdated,
            int templatesRemoved,
            int variablesAdded,
            int variablesRemoved,
            int requiredVariablesAdded,
            int promptsChanged
    ) {
    }
}
