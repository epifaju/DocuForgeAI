package ai.docuforge.privacy;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.domain.PasswordResetTokenRepository;
import ai.docuforge.auth.domain.RefreshTokenRepository;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.domain.document.GeneratedDocument;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.email.EmailDeliveryRepository;
import ai.docuforge.domain.user.UserAccount;
import ai.docuforge.domain.user.UserAccountRepository;
import ai.docuforge.settings.ApplicationSettingsService;
import ai.docuforge.settings.SettingKeys;
import ai.docuforge.storage.StorageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GdprService {

    public static final int DEFAULT_RETENTION_DAYS = 365;

    private final UserAccountRepository userAccountRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final StorageProvider storageProvider;
    private final ApplicationSettingsService applicationSettingsService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    public GdprService(
            UserAccountRepository userAccountRepository,
            GeneratedDocumentRepository generatedDocumentRepository,
            EmailDeliveryRepository emailDeliveryRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            StorageProvider storageProvider,
            ApplicationSettingsService applicationSettingsService,
            AuditService auditService,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.storageProvider = storageProvider;
        this.applicationSettingsService = applicationSettingsService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public int retentionDays(UUID companyId) {
        return applicationSettingsService
                .get(companyId, SettingKeys.DATA_RETENTION_DAYS)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException ex) {
                        return DEFAULT_RETENTION_DAYS;
                    }
                })
                .filter(d -> d > 0)
                .orElse(DEFAULT_RETENTION_DAYS);
    }

    @Transactional(readOnly = true)
    public byte[] exportUserData(DocuForgePrincipal principal) {
        UserAccount user = userAccountRepository
                .findByIdWithCompanyAndRoles(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.user.not_found"));

        List<GeneratedDocument> docs = generatedDocumentRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("company").get("id"), principal.getCompanyId()),
                        cb.equal(root.get("createdBy"), principal.getUserId())
                )
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportedAt", Instant.now().toString());
        payload.put("user", Map.of(
                "id", user.getId().toString(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "companyIdentifier", user.getCompany().getIdentifier(),
                "roles", user.getRoles().stream().map(r -> r.getCode()).toList()
        ));
        List<Map<String, Object>> docRows = new ArrayList<>();
        for (GeneratedDocument d : docs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId().toString());
            row.put("reference", d.getReference());
            row.put("title", d.getTitle());
            row.put("status", d.getStatus().name());
            row.put("createdAt", d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
            row.put("dataSnapshot", d.getDataSnapshot());
            docRows.add(row);
        }
        payload.put("documents", docRows);

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bos)) {
                zip.putNextEntry(new ZipEntry("export.json"));
                zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload));
                zip.closeEntry();
            }
            auditService.recordSuccess(
                    principal,
                    AuditActions.DATA_EXPORTED,
                    "USER",
                    principal.getUserId(),
                    Map.of("documentCount", docs.size())
            );
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", ex);
        }
    }

    @Transactional
    public void deleteDocument(DocuForgePrincipal principal, UUID documentId) {
        GeneratedDocument document = generatedDocumentRepository
                .findByIdAndCompanyId(documentId, principal.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.document.not_found"));
        boolean admin = principal.getRoles().contains("ADMIN");
        if (!admin && (document.getCreatedBy() == null || !document.getCreatedBy().equals(principal.getUserId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.forbidden");
        }
        deleteDocumentInternal(document);
        auditService.recordSuccess(
                principal,
                AuditActions.DOCUMENT_DELETED,
                "DOCUMENT",
                documentId,
                Map.of("reference", document.getReference())
        );
    }

    @Transactional
    public void deleteOwnAccount(DocuForgePrincipal principal) {
        UserAccount user = userAccountRepository
                .findByIdWithCompanyAndRoles(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.user.not_found"));

        List<GeneratedDocument> docs = generatedDocumentRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("company").get("id"), principal.getCompanyId()),
                        cb.equal(root.get("createdBy"), principal.getUserId())
                )
        );
        for (GeneratedDocument doc : docs) {
            deleteDocumentInternal(doc);
        }

        Instant now = Instant.now();
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
        refreshTokenRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.deleteByUserId(user.getId());

        // Soft-delete / anonymize — keep FK integrity for audit_logs / ai_requests.
        user.setEmail("deleted+" + user.getId() + "@invalid.local");
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        user.getRoles().clear();
        userAccountRepository.save(user);

        auditService.recordSuccess(
                principal,
                AuditActions.ACCOUNT_DELETED,
                "USER",
                principal.getUserId(),
                Map.of("documentsDeleted", docs.size(), "anonymized", true)
        );
    }

    @Transactional
    public int purgeExpiredDocuments(DocuForgePrincipal principal) {
        int days = retentionDays(principal.getCompanyId());
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        List<GeneratedDocument> expired = generatedDocumentRepository.findAll(
                (Specification<GeneratedDocument>) (root, query, cb) -> cb.and(
                        cb.equal(root.get("company").get("id"), principal.getCompanyId()),
                        cb.lessThan(root.get("createdAt"), cutoff)
                )
        );
        for (GeneratedDocument doc : expired) {
            deleteDocumentInternal(doc);
        }
        auditService.recordSuccess(
                principal,
                AuditActions.DATA_PURGED,
                "COMPANY",
                principal.getCompanyId(),
                Map.of("deleted", expired.size(), "retentionDays", days, "cutoff", cutoff.toString())
        );
        return expired.size();
    }

    private void deleteDocumentInternal(GeneratedDocument document) {
        emailDeliveryRepository.deleteByDocumentId(document.getId());
        deleteStorageQuietly(document.getDocxStorageKey());
        deleteStorageQuietly(document.getPdfStorageKey());
        generatedDocumentRepository.delete(document);
    }

    private void deleteStorageQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            storageProvider.delete(key);
        } catch (Exception ignored) {
            // best-effort physical cleanup
        }
    }
}
