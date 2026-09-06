package ai.docuforge.domain.businesspack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPackVersionRepository extends JpaRepository<BusinessPackVersion, UUID> {

    Optional<BusinessPackVersion> findByBusinessPackIdAndVersion(UUID businessPackId, String version);

    List<BusinessPackVersion> findByBusinessPackIdOrderByCreatedAtDesc(UUID businessPackId);

    boolean existsByBusinessPackIdAndVersion(UUID businessPackId, String version);
}
