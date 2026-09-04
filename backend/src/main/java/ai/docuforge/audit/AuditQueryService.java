package ai.docuforge.audit;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.audit.dto.AuditLogResponse;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.domain.audit.AuditLog;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.audit.AuditLogSpecs;
import ai.docuforge.domain.user.UserAccount;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final ai.docuforge.domain.user.UserAccountRepository userAccountRepository;

    public AuditQueryService(
            AuditLogRepository auditLogRepository,
            ai.docuforge.domain.user.UserAccountRepository userAccountRepository
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(
            DocuForgePrincipal principal,
            String action,
            String entityType,
            String status,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Page<AuditLog> result = auditLogRepository.findAll(
                AuditLogSpecs.filtered(
                        principal.getCompanyId(),
                        action,
                        entityType,
                        status,
                        createdFrom,
                        createdTo
                ),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Set<UUID> userIds = result.getContent().stream()
                .map(AuditLog::getUser)
                .filter(u -> u != null)
                .map(UserAccount::getId)
                .collect(Collectors.toSet());
        Map<UUID, String> emails = new HashMap<>();
        if (!userIds.isEmpty()) {
            userAccountRepository.findAllById(userIds).forEach(u -> emails.put(u.getId(), u.getEmail()));
        }

        List<AuditLogResponse> items = result.getContent().stream()
                .map(log -> toResponse(log, emails))
                .toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static AuditLogResponse toResponse(AuditLog log, Map<UUID, String> emails) {
        UUID userId = log.getUser() != null ? log.getUser().getId() : null;
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getStatus(),
                log.getIpAddress(),
                log.getMetadata(),
                userId,
                userId != null ? emails.get(userId) : null,
                log.getCreatedAt()
        );
    }
}
