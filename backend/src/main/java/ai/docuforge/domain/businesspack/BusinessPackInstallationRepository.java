package ai.docuforge.domain.businesspack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPackInstallationRepository extends JpaRepository<BusinessPackInstallation, UUID> {

    List<BusinessPackInstallation> findByCompanyIdAndStatus(UUID companyId, PackInstallationStatus status);

    Optional<BusinessPackInstallation> findFirstByCompanyIdAndBusinessPackIdAndStatusOrderByInstalledAtDesc(
            UUID companyId,
            UUID businessPackId,
            PackInstallationStatus status
    );

    Optional<BusinessPackInstallation> findFirstByCompanyIdAndBusinessPackIdOrderByInstalledAtDesc(
            UUID companyId,
            UUID businessPackId
    );
}
