package ai.docuforge.domain.businesspack;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackImportJobRepository extends JpaRepository<PackImportJob, UUID> {

    Optional<PackImportJob> findByIdAndCompanyId(UUID id, UUID companyId);

    List<PackImportJob> findByCompanyIdAndStatus(UUID companyId, PackImportJobStatus status);

    List<PackImportJob> findByExpiresAtBeforeAndStatusIn(Instant cutoff, Collection<PackImportJobStatus> statuses);
}
