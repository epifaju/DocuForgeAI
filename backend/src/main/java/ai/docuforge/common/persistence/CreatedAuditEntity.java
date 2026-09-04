package ai.docuforge.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Extends {@link BaseEntity} with optional {@code created_by}. */
@Getter
@Setter
@MappedSuperclass
public abstract class CreatedAuditEntity extends BaseEntity {

    @Column(name = "created_by")
    private UUID createdBy;
}