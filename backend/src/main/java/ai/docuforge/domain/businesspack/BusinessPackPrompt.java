package ai.docuforge.domain.businesspack;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
        name = "business_pack_prompts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_business_pack_prompts_version_code",
                columnNames = {"business_pack_version_id", "prompt_code", "prompt_version"}
        )
)
public class BusinessPackPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pack_version_id", nullable = false)
    private BusinessPackVersion businessPackVersion;

    @Column(name = "prompt_code", nullable = false, length = 150)
    private String promptCode;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 100)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
