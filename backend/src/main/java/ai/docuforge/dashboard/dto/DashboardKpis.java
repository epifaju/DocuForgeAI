package ai.docuforge.dashboard.dto;

public record DashboardKpis(
        long documentsGeneratedToday,
        long documentsGeneratedThisMonth,
        long activeTemplates,
        long failedGenerations,
        long batchJobsTotal,
        long batchJobsActive,
        long aiRequestsToday,
        long aiRequestsThisMonth
) {
}
