package ai.docuforge.ai;

import ai.docuforge.ai.dto.AiAssistRequest;
import ai.docuforge.ai.dto.AiAssistResponse;
import ai.docuforge.ai.dto.AiStatusResponse;
import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.config.AiProperties;
import ai.docuforge.domain.ai.AiRequestEntity;
import ai.docuforge.domain.ai.AiRequestRepository;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.settings.ApplicationSettingsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiService {

    private final AiProperties properties;
    private final ObjectProvider<AIProvider> aiProvider;
    private final AiRequestRepository aiRequestRepository;
    private final AuditService auditService;
    private final CompanyRepository companyRepository;
    private final ApplicationSettingsService applicationSettingsService;

    public AiService(
            AiProperties properties,
            ObjectProvider<AIProvider> aiProvider,
            AiRequestRepository aiRequestRepository,
            AuditService auditService,
            CompanyRepository companyRepository,
            ApplicationSettingsService applicationSettingsService
    ) {
        this.properties = properties;
        this.aiProvider = aiProvider;
        this.aiRequestRepository = aiRequestRepository;
        this.auditService = auditService;
        this.companyRepository = companyRepository;
        this.applicationSettingsService = applicationSettingsService;
    }

    public AiStatusResponse status() {
        return new AiStatusResponse(
                properties.enabled(),
                properties.provider(),
                properties.ollamaModel()
        );
    }

    @Transactional
    public AiAssistResponse assist(DocuForgePrincipal principal, AiOperation operation, AiAssistRequest request) {
        if (!properties.enabled()
                || !applicationSettingsService.isCompanyAiEnabled(principal.getCompanyId())) {
            throw AiException.disabled();
        }
        AIProvider provider = aiProvider.getIfAvailable();
        if (provider == null) {
            throw AiException.unavailable("Aucun fournisseur IA configure.", null);
        }

        String text = request.text() == null ? "" : request.text().trim();
        if (operation != AiOperation.GENERATE_PARAGRAPH && text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Texte source requis pour cette operation.");
        }
        if (operation == AiOperation.GENERATE_PARAGRAPH
                && text.isBlank()
                && (request.instruction() == null || request.instruction().isBlank())
                && (request.context() == null || request.context().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fournissez une consigne, un contexte ou un texte source."
            );
        }

        AIRequest aiRequest = new AIRequest(operation, text, request.instruction(), request.context());
        try {
            AIResponse response = provider.generate(aiRequest);
            persistRequest(principal, response, operation.name(), "SUCCESS");
            writeAudit(principal, AuditActions.AI_REQUEST, operation.name(), "SUCCESS");
            return new AiAssistResponse(
                    response.content(),
                    operation.name(),
                    response.provider(),
                    response.model(),
                    response.promptVersion(),
                    response.durationMs()
            );
        } catch (AiException ex) {
            persistRequest(principal, null, operation.name(), "FAILED");
            writeAudit(principal, AuditActions.AI_REQUEST, operation.name(), "FAILED");
            throw ex;
        } catch (RuntimeException ex) {
            persistRequest(principal, null, operation.name(), "FAILED");
            writeAudit(principal, AuditActions.AI_REQUEST, operation.name(), "FAILED");
            throw AiException.unavailable("Echec de l'appel IA.", ex);
        }
    }

    private void persistRequest(
            DocuForgePrincipal principal,
            AIResponse response,
            String operation,
            String status
    ) {
        AiRequestEntity row = new AiRequestEntity();
        row.setCompany(companyRepository.getReferenceById(principal.getCompanyId()));
        row.setUserId(principal.getUserId());
        row.setProvider(response != null ? response.provider() : properties.provider());
        row.setModel(response != null ? response.model() : properties.ollamaModel());
        row.setOperation(operation);
        row.setPromptVersion(response != null ? response.promptVersion() : PromptCatalog.PROMPT_VERSION);
        row.setStatus(status);
        row.setDurationMs(response != null ? response.durationMs() : null);
        aiRequestRepository.save(row);
    }

    private void writeAudit(DocuForgePrincipal principal, String action, String entityId, String status) {
        auditService.record(principal, action, "AI", entityId, status, null);
    }
}
