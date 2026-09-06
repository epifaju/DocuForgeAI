package ai.docuforge.businesspack.manifest;

import java.util.List;

public record PackManifestValidationResult(
        boolean valid,
        PackManifest manifest,
        PackManifestSummary summary,
        List<PackValidationIssue> issues
) {
    public record PackManifestSummary(int errors, int warnings, int templates, int prompts) {
    }

    public record PackManifestIdentity(String id, String name, String version) {
    }

    public PackManifestIdentity packIdentity() {
        if (manifest == null) {
            return null;
        }
        return new PackManifestIdentity(manifest.id(), manifest.name(), manifest.version());
    }
}
