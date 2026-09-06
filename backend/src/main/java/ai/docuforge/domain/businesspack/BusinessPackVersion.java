package ai.docuforge.domain.businesspack;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable installed/imported pack version snapshot (manifest JSON retained).
 */
@Getter
@Setter
@Entity
@Table(
        name = "business_pack_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_business_pack_version",
                columnNames = {"business_pack_id", "version"}
        )
)
public class BusinessPackVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pack_id", nullable = false)
    private BusinessPack businessPack;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "schema_version", nullable = false, length = 30)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String manifest;

    @Column(name = "minimum_docuforge_version", length = 50)
    private String minimumDocuForgeVersion;

    @Column(name = "maximum_docuforge_version", length = 50)
    private String maximumDocuForgeVersion;

    @Column(name = "archive_checksum", length = 100)
    private String archiveChecksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PackVersionStatus status = PackVersionStatus.VALIDATING;

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
