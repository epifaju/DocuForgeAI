package ai.docuforge.dashboard.dto;

import ai.docuforge.audit.dto.AuditLogResponse;
import java.util.List;

public record DashboardResponse(
        DashboardKpis kpis,
        List<DashboardRecentDocument> recentDocuments,
        List<AuditLogResponse> recentActivity,
        String timezone
) {
}
