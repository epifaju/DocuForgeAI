package ai.docuforge.businesspack.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Install options for a validated pack import job (PRD §82).
 */
public record PackInstallRequest(
        @NotNull Boolean enablePack,
        @NotNull Boolean enableTemplates
) {
    public PackInstallRequest {
        if (enablePack == null) {
            enablePack = true;
        }
        if (enableTemplates == null) {
            enableTemplates = true;
        }
    }

    public static PackInstallRequest defaults() {
        return new PackInstallRequest(true, true);
    }
}
