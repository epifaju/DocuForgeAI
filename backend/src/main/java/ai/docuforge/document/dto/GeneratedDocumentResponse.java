package ai.docuforge.document.dto;

import ai.docuforge.domain.document.DocumentStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GeneratedDocumentResponse(
        UUID id,
        String reference,
        String title,
        DocumentStatus status,
        UUID templateId,
        String templateCode,
        String templateName,
        UUID templateVersionId,
        Integer templateVersionNumber,
        Integer documentVersionNumber,
        UUID rootDocumentId,
        UUID parentDocumentId,
        String docxStorageKey,
        String pdfStorageKey,
        String checksum,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        Map<String, Object> data
) {
}
