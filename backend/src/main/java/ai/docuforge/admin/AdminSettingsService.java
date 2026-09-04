package ai.docuforge.admin;

import ai.docuforge.admin.dto.AdminSettingsResponse;
import ai.docuforge.admin.dto.AdminSettingsUpdateRequest;
import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.config.AiProperties;
import ai.docuforge.config.MailProperties;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.settings.ApplicationSettingsService;
import ai.docuforge.settings.SettingKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSettingsService {

    private final CompanyRepository companyRepository;
    private final ApplicationSettingsService applicationSettingsService;
    private final AiProperties aiProperties;
    private final MailProperties mailProperties;
    private final AuditService auditService;

    public AdminSettingsService(
            CompanyRepository companyRepository,
            ApplicationSettingsService applicationSettingsService,
            AiProperties aiProperties,
            MailProperties mailProperties,
            AuditService auditService
    ) {
        this.companyRepository = companyRepository;
        this.applicationSettingsService = applicationSettingsService;
        this.aiProperties = aiProperties;
        this.mailProperties = mailProperties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AdminSettingsResponse get(DocuForgePrincipal principal) {
        Company company = requireCompany(principal);
        return toResponse(company);
    }

    @Transactional
    public AdminSettingsResponse update(DocuForgePrincipal principal, AdminSettingsUpdateRequest request) {
        Company company = requireCompany(principal);

        String newName = request.company().name().trim();
        if (newName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de la societe est requis.");
        }

        String previousName = company.getName();
        company.setName(newName);
        companyRepository.save(company);

        boolean companyAiEnabled = Boolean.TRUE.equals(request.ai().companyEnabled());
        applicationSettingsService.putBoolean(
                company.getId(),
                SettingKeys.AI_ENABLED,
                companyAiEnabled
        );

        String from = request.email().fromAddress();
        if (from != null && !from.isBlank()) {
            String trimmed = from.trim();
            if (!trimmed.contains("@") || trimmed.length() < 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adresse expediteur invalide.");
            }
            applicationSettingsService.put(company.getId(), SettingKeys.EMAIL_FROM, trimmed);
        } else {
            applicationSettingsService.put(company.getId(), SettingKeys.EMAIL_FROM, "");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("companyNameChanged", !previousName.equals(newName));
        metadata.put("aiCompanyEnabled", companyAiEnabled);
        metadata.put("emailFromUpdated", true);
        auditService.recordSuccess(
                principal,
                AuditActions.SETTINGS_CHANGED,
                "SETTINGS",
                company.getId(),
                metadata
        );

        return toResponse(company);
    }

    private AdminSettingsResponse toResponse(Company company) {
        boolean companyAi = applicationSettingsService.isCompanyAiEnabled(company.getId());
        boolean platformAi = aiProperties.enabled();
        String emailFrom = applicationSettingsService
                .getEmailFrom(company.getId())
                .orElse(mailProperties.from());

        return new AdminSettingsResponse(
                new AdminSettingsResponse.CompanySettings(
                        company.getId(),
                        company.getName(),
                        company.getIdentifier()
                ),
                new AdminSettingsResponse.AiSettings(
                        platformAi,
                        companyAi,
                        platformAi && companyAi,
                        aiProperties.provider(),
                        aiProperties.ollamaModel()
                ),
                new AdminSettingsResponse.EmailSettings(
                        mailProperties.enabled(),
                        mailProperties.from(),
                        emailFrom,
                        mailProperties.maxAttachmentBytes(),
                        true
                )
        );
    }

    private Company requireCompany(DocuForgePrincipal principal) {
        return companyRepository
                .findById(principal.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Societe introuvable"));
    }
}
