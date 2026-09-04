package ai.docuforge.dashboard;

import ai.docuforge.audit.AuditQueryService;
import ai.docuforge.audit.dto.AuditLogResponse;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.config.DocuForgeProperties;
import ai.docuforge.dashboard.dto.DashboardKpis;
import ai.docuforge.dashboard.dto.DashboardRecentDocument;
import ai.docuforge.dashboard.dto.DashboardResponse;
import ai.docuforge.domain.ai.AiRequestRepository;
import ai.docuforge.domain.batch.BatchJobRepository;
import ai.docuforge.domain.batch.BatchJobStatus;
import ai.docuforge.domain.document.DocumentStatus;
import ai.docuforge.domain.document.GeneratedDocument;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 10;

    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final TemplateRepository templateRepository;
    private final BatchJobRepository batchJobRepository;
    private final AiRequestRepository aiRequestRepository;
    private final AuditQueryService auditQueryService;
    private final DocuForgeProperties properties;
    private final Clock clock;

    public DashboardService(
            GeneratedDocumentRepository generatedDocumentRepository,
            TemplateRepository templateRepository,
            BatchJobRepository batchJobRepository,
            AiRequestRepository aiRequestRepository,
            AuditQueryService auditQueryService,
            DocuForgeProperties properties,
            Clock clock
    ) {
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.templateRepository = templateRepository;
        this.batchJobRepository = batchJobRepository;
        this.aiRequestRepository = aiRequestRepository;
        this.auditQueryService = auditQueryService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(DocuForgePrincipal principal) {
        ZoneId zone = resolveZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant startOfDay = today.atStartOfDay(zone).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant();

        var companyId = principal.getCompanyId();

        DashboardKpis kpis = new DashboardKpis(
                generatedDocumentRepository.countByCompany_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        companyId, startOfDay, startOfTomorrow
                ),
                generatedDocumentRepository.countByCompany_IdAndCreatedAtGreaterThanEqual(
                        companyId, startOfMonth
                ),
                templateRepository.countByCompany_IdAndStatus(companyId, TemplateStatus.ACTIVE),
                generatedDocumentRepository.countByCompany_IdAndStatus(companyId, DocumentStatus.FAILED),
                batchJobRepository.countByCompany_Id(companyId),
                batchJobRepository.countByCompany_IdAndStatusIn(
                        companyId,
                        EnumSet.of(BatchJobStatus.CREATED, BatchJobStatus.VALIDATING, BatchJobStatus.PROCESSING)
                ),
                aiRequestRepository.countByCompany_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        companyId, startOfDay, startOfTomorrow
                ),
                aiRequestRepository.countByCompany_IdAndCreatedAtGreaterThanEqual(companyId, startOfMonth)
        );

        List<DashboardRecentDocument> recentDocuments = generatedDocumentRepository
                .findTop10ByCompany_IdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(this::toRecentDocument)
                .toList();

        PageResponse<AuditLogResponse> activity = auditQueryService.list(
                principal, null, null, null, null, null, 0, RECENT_LIMIT
        );

        return new DashboardResponse(kpis, recentDocuments, activity.items(), zone.getId());
    }

    private DashboardRecentDocument toRecentDocument(GeneratedDocument document) {
        return new DashboardRecentDocument(
                document.getId(),
                document.getReference(),
                document.getTitle(),
                document.getStatus().name(),
                document.getTemplate() != null ? document.getTemplate().getName() : null,
                document.getCreatedAt()
        );
    }

    private ZoneId resolveZone() {
        try {
            return ZoneId.of(properties.timezone() == null || properties.timezone().isBlank()
                    ? "Europe/Paris"
                    : properties.timezone());
        } catch (Exception ex) {
            return ZoneId.of("Europe/Paris");
        }
    }
}
