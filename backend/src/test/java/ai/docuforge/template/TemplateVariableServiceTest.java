package ai.docuforge.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateOrigin;
import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.template.VariableType;
import ai.docuforge.template.dto.TemplateVariableUpdateItem;
import ai.docuforge.template.dto.TemplateVariablesUpdateRequest;
import ai.docuforge.template.parser.DetectedVariable;
import ai.docuforge.template.parser.DocxVariableParser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TemplateVariableServiceTest {

    @Mock private TemplateVersionRepository templateVersionRepository;
    @Mock private TemplateVariableRepository templateVariableRepository;
    @Mock private DocxVariableParser docxVariableParser;
    @Mock private AuditService auditService;

    private TemplateVariableService service;
    private DocuForgePrincipal principal;
    private final UUID companyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TemplateVariableService(
                templateVersionRepository,
                templateVariableRepository,
                docxVariableParser,
                auditService
        );
        principal = new DocuForgePrincipal(
                userId, companyId, "demo", "admin@demo.local", "h", true, Set.of("ADMIN")
        );
    }

    @Test
    void createFromDocx_parsesAndPersists() {
        TemplateVersion version = version(TemplateOrigin.USER);
        byte[] bytes = {1, 2, 3};
        when(docxVariableParser.parse(bytes)).thenReturn(List.of(
                new DetectedVariable("client.name", "Client", VariableType.TEXT, 0)
        ));
        when(templateVariableRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var saved = service.createFromDocx(version, bytes);

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getVariableKey()).isEqualTo("client.name");
        assertThat(saved.getFirst().isRequired()).isTrue();
        verify(docxVariableParser).parse(bytes);
    }

    @Test
    void copyFromVersion_clonesAllFields() {
        TemplateVariable source = variable("a.key", "Label", VariableType.EMAIL, 2);
        source.setRequired(false);
        source.setDefaultValue("x");
        source.setPlaceholder("ph");
        source.setConfiguration("{\"min\":1}");
        TemplateVersion target = version(TemplateOrigin.USER);
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of(source));
        when(templateVariableRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.copyFromVersion(versionId, target);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TemplateVariable>> captor = ArgumentCaptor.forClass(List.class);
        verify(templateVariableRepository).saveAll(captor.capture());
        TemplateVariable copy = captor.getValue().getFirst();
        assertThat(copy.getTemplateVersion()).isSameAs(target);
        assertThat(copy.getVariableKey()).isEqualTo("a.key");
        assertThat(copy.getLabel()).isEqualTo("Label");
        assertThat(copy.getType()).isEqualTo(VariableType.EMAIL);
        assertThat(copy.isRequired()).isFalse();
        assertThat(copy.getDefaultValue()).isEqualTo("x");
        assertThat(copy.getPlaceholder()).isEqualTo("ph");
        assertThat(copy.getDisplayOrder()).isEqualTo(2);
        assertThat(copy.getConfiguration()).isEqualTo("{\"min\":1}");
    }

    @Test
    void list_mapsResponses() {
        when(templateVersionRepository.findByIdAndCompanyId(versionId, companyId))
                .thenReturn(Optional.of(version(TemplateOrigin.USER)));
        TemplateVariable v = variable("client.name", "Client", VariableType.TEXT, 0);
        v.setId(UUID.randomUUID());
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of(v));

        var responses = service.list(principal, versionId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().key()).isEqualTo("client.name");
        assertThat(responses.getFirst().label()).isEqualTo("Client");
    }

    @Test
    void list_notFound() {
        when(templateVersionRepository.findByIdAndCompanyId(versionId, companyId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(principal, versionId))
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_rejectsPackTemplates() {
        when(templateVersionRepository.findByIdAndCompanyId(versionId, companyId))
                .thenReturn(Optional.of(version(TemplateOrigin.PACK)));
        assertThatThrownBy(() -> service.update(
                principal, versionId, new TemplateVariablesUpdateRequest(List.of())
        )).hasMessageContaining("pack_immutable");
    }

    @Test
    void update_rejectsInvalidDuplicateUnknownAndIncomplete() {
        when(templateVersionRepository.findByIdAndCompanyId(versionId, companyId))
                .thenReturn(Optional.of(version(TemplateOrigin.USER)));
        TemplateVariable existing = variable("client.name", "Client", VariableType.TEXT, 0);
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.update(
                principal,
                versionId,
                new TemplateVariablesUpdateRequest(List.of(
                        item("!!!", "Bad", VariableType.TEXT, true, null)
                ))
        )).hasMessageContaining("invalide");

        assertThatThrownBy(() -> service.update(
                principal,
                versionId,
                new TemplateVariablesUpdateRequest(List.of(
                        item("client.name", "A", VariableType.TEXT, true, 0),
                        item("client.name", "B", VariableType.TEXT, true, 1)
                ))
        )).hasMessageContaining("double");

        assertThatThrownBy(() -> service.update(
                principal,
                versionId,
                new TemplateVariablesUpdateRequest(List.of(
                        item("other.key", "Other", VariableType.TEXT, true, 0)
                ))
        )).hasMessageContaining("inconnue");

        assertThatThrownBy(() -> service.update(
                principal,
                versionId,
                new TemplateVariablesUpdateRequest(List.of())
        )).hasMessageContaining("variables_incomplete");
    }

    @Test
    void update_persistsMetadataAndAudits() {
        when(templateVersionRepository.findByIdAndCompanyId(versionId, companyId))
                .thenReturn(Optional.of(version(TemplateOrigin.USER)));
        TemplateVariable existing = variable("client.name", "Old", VariableType.TEXT, 0);
        existing.setId(UUID.randomUUID());
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of(existing));
        when(templateVariableRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var responses = service.update(
                principal,
                versionId,
                new TemplateVariablesUpdateRequest(List.of(
                        new TemplateVariableUpdateItem(
                                "client.name",
                                "Nouveau",
                                VariableType.EMAIL,
                                false,
                                "a@b.c",
                                "email",
                                5,
                                "{\"x\":1}"
                        )
                ))
        );

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().label()).isEqualTo("Nouveau");
        assertThat(responses.getFirst().type()).isEqualTo(VariableType.EMAIL);
        assertThat(responses.getFirst().required()).isFalse();
        assertThat(responses.getFirst().displayOrder()).isEqualTo(5);
        assertThat(existing.getDefaultValue()).isEqualTo("a@b.c");
        verify(auditService).recordSuccess(eq(principal), anyString(), eq("TEMPLATE"), eq(templateId));
    }

    @Test
    void update_keepsDisplayOrderWhenNull() {
        when(templateVersionRepository.findByIdAndCompanyId(versionId, companyId))
                .thenReturn(Optional.of(version(TemplateOrigin.USER)));
        TemplateVariable existing = variable("client.name", "Client", VariableType.TEXT, 7);
        when(templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId))
                .thenReturn(List.of(existing));
        when(templateVariableRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.update(
                principal,
                versionId,
                new TemplateVariablesUpdateRequest(List.of(
                        item("client.name", "Client", VariableType.TEXT, true, null)
                ))
        );

        assertThat(existing.getDisplayOrder()).isEqualTo(7);
    }

    private TemplateVersion version(TemplateOrigin origin) {
        Template template = new Template();
        template.setId(templateId);
        template.setOrigin(origin);
        TemplateVersion version = new TemplateVersion();
        version.setId(versionId);
        version.setTemplate(template);
        version.setVersionNumber(1);
        return version;
    }

    private TemplateVariable variable(String key, String label, VariableType type, int order) {
        TemplateVariable variable = new TemplateVariable();
        variable.setVariableKey(key);
        variable.setLabel(label);
        variable.setType(type);
        variable.setRequired(true);
        variable.setDisplayOrder(order);
        return variable;
    }

    private TemplateVariableUpdateItem item(
            String key, String label, VariableType type, boolean required, Integer order
    ) {
        return new TemplateVariableUpdateItem(key, label, type, required, null, null, order, null);
    }
}
