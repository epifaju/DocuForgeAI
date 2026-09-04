package ai.docuforge.email.dto;

import ai.docuforge.domain.email.AttachmentFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentEmailRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String recipient,

        @NotBlank
        @Size(max = 255)
        String subject,

        @Size(max = 5000)
        String message,

        @NotNull
        AttachmentFormat attachmentFormat,

        @AssertTrue(message = "Confirmation requise avant envoi.")
        boolean confirmed
) {
}
