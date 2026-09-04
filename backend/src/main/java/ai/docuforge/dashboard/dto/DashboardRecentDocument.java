package ai.docuforge.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardRecentDocument(
        UUID id,
        String reference,
        String title,
        String status,
        String templateName,
        Instant createdAt
) {
}
