package ai.docuforge.businesspack.dto;

import ai.docuforge.domain.template.TemplateStatus;
import java.util.UUID;

/**
 * Template link installed by a pack (PRD §§84, §86).
 */
public record PackTemplateResponse(
        UUID id,
        String templateCode,
        String name,
        TemplateStatus templateStatus,
        UUID templateId,
        UUID templateVersionId,
        Integer templateVersionNumber,
        boolean enabledByDefault,
        boolean enabled
) {
}
