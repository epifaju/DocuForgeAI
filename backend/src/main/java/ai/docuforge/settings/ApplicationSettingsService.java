package ai.docuforge.settings;

import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.settings.ApplicationSetting;
import ai.docuforge.domain.settings.ApplicationSettingRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicationSettingsService {

    private final ApplicationSettingRepository settingRepository;
    private final CompanyRepository companyRepository;

    public ApplicationSettingsService(
            ApplicationSettingRepository settingRepository,
            CompanyRepository companyRepository
    ) {
        this.settingRepository = settingRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(UUID companyId, String key) {
        return settingRepository
                .findByCompanyIdAndSettingKey(companyId, key)
                .map(ApplicationSetting::getSettingValue);
    }

    @Transactional(readOnly = true)
    public boolean isCompanyAiEnabled(UUID companyId) {
        return get(companyId, SettingKeys.AI_ENABLED)
                .map(v -> !"false".equalsIgnoreCase(v.trim()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public Optional<String> getEmailFrom(UUID companyId) {
        return get(companyId, SettingKeys.EMAIL_FROM)
                .map(String::trim)
                .filter(v -> !v.isBlank());
    }

    @Transactional
    public void put(UUID companyId, String key, String value) {
        Company company = companyRepository
                .findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Societe introuvable"));

        ApplicationSetting setting = settingRepository
                .findByCompanyIdAndSettingKey(companyId, key)
                .orElseGet(() -> {
                    ApplicationSetting created = new ApplicationSetting();
                    created.setCompany(company);
                    created.setSettingKey(key);
                    return created;
                });
        setting.setSettingValue(value);
        settingRepository.save(setting);
    }

    @Transactional
    public void putBoolean(UUID companyId, String key, boolean value) {
        put(companyId, key, value ? "true" : "false");
    }
}
