package ai.docuforge.domain.businesspack;

import ai.docuforge.domain.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Async/sync import staging job (same shape as batch_jobs for future SKIP LOCKED workers).
 */
@Getter
@Setter
@Entity
@Table(name = "pack_import_jobs")
public class PackImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PackImportJobStatus status = PackImportJobStatus.UPLOADED;

    @Column(name = "detected_pack_key", length = 255)
    private String detectedPackKey;

    @Column(name = "detected_version", length = 50)
    private String detectedVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_report", columnDefinition = "jsonb")
    private String validationReport;

    @Column(name = "staging_storage_key", length = 500)
    private String stagingStorageKey;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
