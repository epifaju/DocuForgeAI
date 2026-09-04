package ai.docuforge.domain.settings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, UUID> {

    List<ApplicationSetting> findByCompanyId(UUID companyId);

    Optional<ApplicationSetting> findByCompanyIdAndSettingKey(UUID companyId, String settingKey);
}
