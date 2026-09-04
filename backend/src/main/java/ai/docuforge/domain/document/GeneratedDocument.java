package ai.docuforge.domain.document;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(
        name = "generated_documents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_generated_documents_company_reference",
                        columnNames = {"company_id", "reference"}
                ),
                @UniqueConstraint(
                        name = "uq_generated_documents_root_version",
                        columnNames = {"root_document_id", "document_version_number"}
                )
        }
)
public class GeneratedDocument {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id", nullable = false)
    private TemplateVersion templateVersion;

    @Column(name = "root_document_id", nullable = false)
    private UUID rootDocumentId;

    @Column(name = "parent_document_id")
    private UUID parentDocumentId;

    @Column(name = "document_version_number", nullable = false)
    private Integer documentVersionNumber;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_snapshot", nullable = false, columnDefinition = "jsonb")
    private String dataSnapshot;

    @Column(name = "docx_storage_key", length = 500)
    private String docxStorageKey;

    @Column(name = "pdf_storage_key", length = 500)
    private String pdfStorageKey;

    @Column(length = 64)
    private String checksum;

    @Column(name = "created_by")
    private UUID createdBy;

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
        if (rootDocumentId == null) {
            rootDocumentId = id;
        }
        if (documentVersionNumber == null) {
            documentVersionNumber = 1;
        }
    }
}
