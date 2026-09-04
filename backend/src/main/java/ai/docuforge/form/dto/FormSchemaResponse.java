package ai.docuforge.form.dto;

import java.util.List;
import java.util.UUID;

public record FormSchemaResponse(
        UUID templateVersionId,
        UUID templateId,
        String templateCode,
        String templateName,
        int versionNumber,
        List<FormFieldSchema> fields
) {
}