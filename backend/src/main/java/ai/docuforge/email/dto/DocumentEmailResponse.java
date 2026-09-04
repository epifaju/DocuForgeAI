package ai.docuforge.email.dto;

import ai.docuforge.domain.email.AttachmentFormat;
import ai.docuforge.domain.email.EmailDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentEmailResponse(
        UUID id,
        UUID documentId,
        String recipient,
        String subject,
        AttachmentFormat attachmentFormat,
        EmailDeliveryStatus status,
        Instant createdAt,
        Instant sentAt
) {
}
