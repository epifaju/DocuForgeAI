package ai.docuforge.businesspack.update;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import java.util.List;

public record PackChangeAnalysis(
        int templatesAdded,
        int templatesUpdated,
        int templatesRemoved,
        int variablesAdded,
        int variablesRemoved,
        int requiredVariablesAdded,
        int promptsChanged,
        List<PackChangeItem> changes,
        List<PackValidationIssue> breakingChanges
) {
    public boolean hasBreakingChanges() {
        return breakingChanges != null && !breakingChanges.isEmpty();
    }
}
