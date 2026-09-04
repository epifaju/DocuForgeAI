package ai.docuforge.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String companyIdentifier,
        @NotBlank @Email String email,
        @NotBlank String password
) {
}