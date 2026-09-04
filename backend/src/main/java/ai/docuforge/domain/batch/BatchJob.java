package ai.docuforge.domain.batch;

import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

@Getter
@Setter
@Entity
@Table(name = "batch_jobs")
public class BatchJob {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private Template template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id", nullable = false)
    private TemplateVersion templateVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BatchJobStatus status;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "processed_items", nullable = false)
    private int processedItems;

    @Column(name = "successful_items", nullable = false)
    private int successfulItems;

    @Column(name = "failed_items", nullable = false)
    private int failedItems;

    @Column(name = "csv_storage_key", length = 500)
    private String csvStorageKey;

    @Column(name = "zip_storage_key", length = 500)
    private String zipStorageKey;

    @Column(name = "error_report_storage_key", length = 500)
    private String errorReportStorageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping", columnDefinition = "jsonb")
    private String columnMapping;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
