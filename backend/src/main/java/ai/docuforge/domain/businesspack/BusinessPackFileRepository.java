package ai.docuforge.domain.businesspack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPackFileRepository extends JpaRepository<BusinessPackFile, UUID> {

    List<BusinessPackFile> findByBusinessPackVersionId(UUID businessPackVersionId);

    Optional<BusinessPackFile> findByBusinessPackVersionIdAndLogicalPath(
            UUID businessPackVersionId,
            String logicalPath
    );

    void deleteByBusinessPackVersionId(UUID businessPackVersionId);
}
