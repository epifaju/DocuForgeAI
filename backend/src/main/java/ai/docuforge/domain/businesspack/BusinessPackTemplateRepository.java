package ai.docuforge.domain.businesspack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPackTemplateRepository extends JpaRepository<BusinessPackTemplate, UUID> {

    List<BusinessPackTemplate> findByBusinessPackVersionId(UUID businessPackVersionId);

    Optional<BusinessPackTemplate> findByBusinessPackVersionIdAndTemplateCode(
            UUID businessPackVersionId,
            String templateCode
    );

    boolean existsByBusinessPackVersionIdAndTemplateCode(UUID businessPackVersionId, String templateCode);

    void deleteByBusinessPackVersionId(UUID businessPackVersionId);
}
