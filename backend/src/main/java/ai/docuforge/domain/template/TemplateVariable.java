package ai.docuforge.domain.template;

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

@Getter
@Setter
@Entity
@Table(
        name = "template_variables",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_template_variables_version_key",
                columnNames = {"template_version_id", "variable_key"}
        )
)
public class TemplateVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_version_id", nullable = false)
    private TemplateVersion templateVersion;

    @Column(name = "variable_key", nullable = false, length = 200)
    private String variableKey;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VariableType type;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(columnDefinition = "TEXT")
    private String placeholder;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String configuration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}