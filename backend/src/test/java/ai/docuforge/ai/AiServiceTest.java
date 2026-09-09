package ai.docuforge.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.ai.dto.AiAssistRequest;
import ai.docuforge.ai.dto.AiAssistResponse;
import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.config.AiProperties;
import ai.docuforge.domain.ai.AiRequestEntity;
import ai.docuforge.domain.ai.AiRequestRepository;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.settings.ApplicationSettingsService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock private ObjectProvider<AIProvider> aiProvider;
    @Mock private AiRequestRepository aiRequestRepository;
    @Mock private AuditService auditService;
    @Mock private CompanyRepository companyRepository;
    @Mock private ApplicationSettingsService applicationSettingsService;
    @Mock private AIProvider provider;

    private AiProperties properties;
    private AiService service;
    private DocuForgePrincipal principal;
    private final UUID companyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new AiProperties(true, "ollama", "http://localhost", "llama", 30, 1);
        service = new AiService(
                properties,
                aiProvider,
                aiRequestRepository,
                auditService,
                companyRepository,
                applicationSettingsService
        );
        principal = new DocuForgePrincipal(
                userId, companyId, "ai-co", "admin@ai-co.test", "hash", true, Set.of("ADMIN")
        );
    }

    @Test
    void rejectsBlankTextForRewrite() {
        when(applicationSettingsService.isCompanyAiEnabled(companyId)).thenReturn(true);
        when(aiProvider.getIfAvailable()).thenReturn(provider);

        assertThatThrownBy(() -> service.assist(principal, AiOperation.REWRITE, new AiAssistRequest("  ", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsWhenGlobalAiDisabled() {
        service = new AiService(
                new AiProperties(false, "ollama", "http://localhost", "llama", 30, 1),
                aiProvider,
                aiRequestRepository,
                auditService,
                companyRepository,
                applicationSettingsService
        );

        assertThatThrownBy(() -> service.assist(principal, AiOperation.REWRITE, new AiAssistRequest("hi", null, null)))
                .isInstanceOf(AiException.class);
    }

    @Test
    void rejectsWhenCompanyAiDisabled() {
        when(applicationSettingsService.isCompanyAiEnabled(companyId)).thenReturn(false);

        assertThatThrownBy(() -> service.assist(principal, AiOperation.REWRITE, new AiAssistRequest("hi", null, null)))
                .isInstanceOf(AiException.class);
    }

    @Test
    void rejectsWhenProviderMissing() {
        when(applicationSettingsService.isCompanyAiEnabled(companyId)).thenReturn(true);
        when(aiProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.assist(principal, AiOperation.REWRITE, new AiAssistRequest("hi", null, null)))
                .isInstanceOf(AiException.class);
    }

    @Test
    void successPersistsAuditAndRequest() {
        when(applicationSettingsService.isCompanyAiEnabled(companyId)).thenReturn(true);
        when(aiProvider.getIfAvailable()).thenReturn(provider);
        when(provider.generate(any())).thenReturn(
                new AIResponse("rewritten", "ollama", "llama", PromptCatalog.PROMPT_VERSION, 5L)
        );
        Company company = new Company();
        company.setId(companyId);
        when(companyRepository.getReferenceById(companyId)).thenReturn(company);

        AiAssistResponse response = service.assist(
                principal, AiOperation.REWRITE, new AiAssistRequest("bonjour", null, null)
        );

        assertThat(response.result()).isEqualTo("rewritten");
        ArgumentCaptor<AiRequestEntity> captor = ArgumentCaptor.forClass(AiRequestEntity.class);
        verify(aiRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCESS");
        verify(auditService).record(eq(principal), eq(AuditActions.AI_REQUEST), eq("AI"), eq("REWRITE"), eq("SUCCESS"), eq(null));
    }

    @Test
    void failurePersistsFailedStatus() {
        when(applicationSettingsService.isCompanyAiEnabled(companyId)).thenReturn(true);
        when(aiProvider.getIfAvailable()).thenReturn(provider);
        when(provider.generate(any())).thenThrow(AiException.unavailable("down", null));
        Company company = new Company();
        company.setId(companyId);
        when(companyRepository.getReferenceById(companyId)).thenReturn(company);

        assertThatThrownBy(() -> service.assist(principal, AiOperation.REWRITE, new AiAssistRequest("bonjour", null, null)))
                .isInstanceOf(AiException.class);

        ArgumentCaptor<AiRequestEntity> captor = ArgumentCaptor.forClass(AiRequestEntity.class);
        verify(aiRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        verify(auditService).record(eq(principal), eq(AuditActions.AI_REQUEST), eq("AI"), eq("REWRITE"), eq("FAILED"), eq(null));
        verify(aiRequestRepository, never()).delete(any());
    }
}
