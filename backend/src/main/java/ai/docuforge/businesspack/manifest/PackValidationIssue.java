package ai.docuforge.businesspack.manifest;

/**
 * Single validation issue (PRD §§66–68). Optional context fields may be null.
 */
public record PackValidationIssue(
        PackValidationSeverity severity,
        String code,
        String message,
        String file,
        String templateCode,
        String variable
) {
    public static PackValidationIssue error(String code, String message) {
        return new PackValidationIssue(PackValidationSeverity.ERROR, code, message, null, null, null);
    }

    public static PackValidationIssue warning(String code, String message) {
        return new PackValidationIssue(PackValidationSeverity.WARNING, code, message, null, null, null);
    }

    public static PackValidationIssue warning(String code, String message, String file) {
        return new PackValidationIssue(PackValidationSeverity.WARNING, code, message, file, null, null);
    }

    public PackValidationIssue withTemplateCode(String code) {
        return new PackValidationIssue(severity, this.code, message, file, code, variable);
    }

    public PackValidationIssue withFile(String file) {
        return new PackValidationIssue(severity, code, message, file, templateCode, variable);
    }

    public PackValidationIssue withVariable(String variable) {
        return new PackValidationIssue(severity, code, message, file, templateCode, variable);
    }
}
