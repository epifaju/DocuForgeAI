package ai.docuforge.template.dto;

import ai.docuforge.domain.template.TemplateOrigin;
import ai.docuforge.domain.template.TemplateStatus;
import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String code,
        String name,
        String description,
        String category,
        TemplateStatus status,
        TemplateOrigin origin,
        UUID sourcePackId,
        Integer currentVersionNumber,
        UUID currentVersionId,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}