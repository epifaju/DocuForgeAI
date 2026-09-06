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

@Getter
@Setter
@Entity
@Table(name = "business_pack_installations")
public class BusinessPackInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pack_id", nullable = false)
    private BusinessPack businessPack;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_pack_version_id", nullable = false)
    private BusinessPackVersion businessPackVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "installation_type", nullable = false, length = 30)
    private PackInstallationType installationType;

    @Column(name = "installed_by")
    private UUID installedBy;

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "uninstalled_at")
    private Instant uninstalledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PackInstallationStatus status = PackInstallationStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @PrePersist
    void onCreate() {
        if (installedAt == null) {
            installedAt = Instant.now();
        }
    }
}
