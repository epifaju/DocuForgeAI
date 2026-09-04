package ai.docuforge.audit;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.domain.audit.AuditLog;
import ai.docuforge.domain.audit.AuditLogRepository;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Set<String> FORBIDDEN_METADATA_KEYS = Set.of(
            "password",
            "jwt",
            "token",
            "accessToken",
            "refreshToken",
            "apikey",
            "api_key",
            "apiKey",
            "secret",
            "smtpPassword",
            "smtp_password"
    );

    private final AuditLogRepository auditLogRepository;
    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditLogRepository auditLogRepository,
            CompanyRepository companyRepository,
            UserAccountRepository userAccountRepository,
            ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            DocuForgePrincipal principal,
            String action,
            String entityType,
            String entityId,
            String status,
            Map<String, ?> metadata
    ) {
        AuditLog log = new AuditLog();
        log.setCompany(companyRepository.getReferenceById(principal.getCompanyId()));
        log.setUser(userAccountRepository.getReferenceById(principal.getUserId()));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStatus(status);
        log.setMetadata(toMetadataJson(metadata));
        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            DocuForgePrincipal principal,
            String action,
            String entityType,
            UUID entityId,
            String status
    ) {
        record(principal, action, entityType, entityId != null ? entityId.toString() : null, status, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            DocuForgePrincipal principal,
            String action,
            String entityType,
            UUID entityId
    ) {
        record(principal, action, entityType, entityId, "SUCCESS");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            DocuForgePrincipal principal,
            String action,
            String entityType,
            UUID entityId,
            Map<String, ?> metadata
    ) {
        record(
                principal,
                action,
                entityType,
                entityId != null ? entityId.toString() : null,
                "SUCCESS",
                metadata
        );
    }

    /**
     * Login / pre-auth flows where a {@link DocuForgePrincipal} is not yet available.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordForUser(
            UserAccount user,
            String action,
            String entityType,
            String entityId,
            String status,
            String ipAddress,
            Map<String, ?> metadata
    ) {
        AuditLog log = new AuditLog();
        log.setCompany(user.getCompany());
        log.setUser(user);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStatus(status);
        log.setIpAddress(ipAddress);
        log.setMetadata(toMetadataJson(metadata));
        auditLogRepository.save(log);
    }

    /**
     * Async / system completions without an authenticated principal (e.g. batch finish).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(
            UUID companyId,
            UUID userId,
            String action,
            String entityType,
            String entityId,
            String status,
            Map<String, ?> metadata
    ) {
        AuditLog log = new AuditLog();
        log.setCompany(companyRepository.getReferenceById(companyId));
        if (userId != null) {
            log.setUser(userAccountRepository.getReferenceById(userId));
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStatus(status);
        log.setMetadata(toMetadataJson(metadata));
        auditLogRepository.save(log);
    }

    private String toMetadataJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String key : metadata.keySet()) {
            if (key == null) {
                continue;
            }
            String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            if (FORBIDDEN_METADATA_KEYS.stream()
                    .map(f -> f.toLowerCase(Locale.ROOT).replace("-", "").replace("_", ""))
                    .anyMatch(normalized::equals)) {
                throw new IllegalArgumentException("Metadata audit interdit pour la cle: " + key);
            }
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
