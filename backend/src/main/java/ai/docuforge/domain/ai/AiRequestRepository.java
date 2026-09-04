package ai.docuforge.domain.ai;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestRepository extends JpaRepository<AiRequestEntity, UUID> {

    long countByCompany_IdAndCreatedAtGreaterThanEqual(UUID companyId, Instant from);

    long countByCompany_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID companyId,
            Instant from,
            Instant to
    );
}
