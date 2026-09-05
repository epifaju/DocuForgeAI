package ai.docuforge.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSettingsUpdateRequest(
        @Valid @NotNull CompanyUpdate company,
        @Valid @NotNull AiUpdate ai,
        @Valid @NotNull EmailUpdate email,
        @Valid @NotNull PrivacyUpdate privacy
) {
    public record CompanyUpdate(
            @NotBlank @Size(max = 200) String name
    ) {
    }

    public record AiUpdate(
            @NotNull Boolean companyEnabled
    ) {
    }

    public record EmailUpdate(
            @Size(max = 255) String fromAddress
    ) {
    }

    public record PrivacyUpdate(
            @NotNull @Min(30) @Max(3650) Integer retentionDays
    ) {
    }
}
