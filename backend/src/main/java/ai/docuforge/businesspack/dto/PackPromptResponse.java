package ai.docuforge.businesspack.dto;

import java.util.UUID;

/**
 * Prompt installed with a pack version (PRD §84). Full content for ADMIN.
 */
public record PackPromptResponse(
        UUID id,
        String promptCode,
        String promptVersion,
        String content,
        String checksum
) {
}
