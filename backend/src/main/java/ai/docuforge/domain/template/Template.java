package ai.docuforge.domain.template;

import ai.docuforge.common.persistence.CreatedAuditEntity;
import ai.docuforge.domain.businesspack.BusinessPack;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "templates",
        uniqueConstraints = @UniqueConstraint(name = "uq_templates_company_code", columnNames = {"company_id", "code"})
)
public class Template extends CreatedAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TemplateStatus status = TemplateStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TemplateOrigin origin = TemplateOrigin.USER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_pack_id")
    private BusinessPack sourcePack;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private TemplateVersion currentVersion;
}