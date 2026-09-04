package ai.docuforge.domain.batch;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "batch_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_items_job_row",
                columnNames = {"batch_job_id", "row_number"}
        )
)
public class BatchItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_job_id", nullable = false)
    private BatchJob batchJob;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BatchItemStatus status;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "generated_document_id")
    private UUID generatedDocumentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
