package ai.docuforge.domain.businesspack;

import ai.docuforge.common.persistence.BaseEntity;
import ai.docuforge.domain.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Business pack catalog/install header (DBPF-1 domain).
 * {@code company} null = global catalog row (typical OFFICIAL definition);
 * non-null = company-scoped custom/third-party pack.
 */
@Getter
@Setter
@Entity
@Table(name = "business_packs")
public class BusinessPack extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "pack_key", nullable = false, length = 255)
    private String packKey;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "pack_type", nullable = false, length = 30)
    private BusinessPackType packType;

    @Column(name = "publisher_id", length = 255)
    private String publisherId;

    @Column(name = "publisher_name", length = 255)
    private String publisherName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessPackStatus status = BusinessPackStatus.INSTALLED;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private BusinessPackVersion currentVersion;
}
