package ai.docuforge.admin.dto;

import java.util.UUID;

public record AdminSettingsResponse(
        CompanySettings company,
        AiSettings ai,
        EmailSettings email,
        PrivacySettings privacy
) {
    public record CompanySettings(UUID id, String name, String identifier) {
    }

    public record AiSettings(
            boolean platformEnabled,
            boolean companyEnabled,
            boolean effectivelyEnabled,
            String provider,
            String model
    ) {
    }

    public record EmailSettings(
            boolean platformEnabled,
            String platformFrom,
            String fromAddress,
            long maxAttachmentBytes,
            boolean requireConfirmation
    ) {
    }

    public record PrivacySettings(int retentionDays) {
    }
}
