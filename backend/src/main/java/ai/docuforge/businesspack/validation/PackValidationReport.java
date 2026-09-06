package ai.docuforge.businesspack.validation;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import java.util.List;

/**
 * Aggregate pack validation report (PRD §§66–68).
 */
public record PackValidationReport(
        boolean valid,
        PackIdentity pack,
        PackSummary summary,
        List<PackValidationIssue> issues
) {
    public record PackIdentity(String id, String name, String version) {
    }

    public record PackSummary(int errors, int warnings, int templates, int prompts) {
    }

    public static PackValidationReport of(PackIdentity pack, List<PackValidationIssue> issues, int templates, int prompts) {
        int errors = (int) issues.stream()
                .filter(i -> i.severity() == ai.docuforge.businesspack.manifest.PackValidationSeverity.ERROR)
                .count();
        int warnings = (int) issues.stream()
                .filter(i -> i.severity() == ai.docuforge.businesspack.manifest.PackValidationSeverity.WARNING)
                .count();
        return new PackValidationReport(
                errors == 0,
                pack,
                new PackSummary(errors, warnings, templates, prompts),
                List.copyOf(issues)
        );
    }
}
