package ai.docuforge.template;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.template.dto.TemplateVariableResponse;
import ai.docuforge.template.dto.TemplateVariableUpdateItem;
import ai.docuforge.template.dto.TemplateVariablesUpdateRequest;
import ai.docuforge.template.parser.DetectedVariable;
import ai.docuforge.template.parser.DocxVariableParser;
import ai.docuforge.template.parser.VariableKeyRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TemplateVariableService {

    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateVariableRepository templateVariableRepository;
    private final DocxVariableParser docxVariableParser;
    private final AuditService auditService;

    public TemplateVariableService(
            TemplateVersionRepository templateVersionRepository,
            TemplateVariableRepository templateVariableRepository,
            DocxVariableParser docxVariableParser,
            AuditService auditService
    ) {
        this.templateVersionRepository = templateVersionRepository;
        this.templateVariableRepository = templateVariableRepository;
        this.docxVariableParser = docxVariableParser;
        this.auditService = auditService;
    }

    @Transactional
    public List<TemplateVariable> createFromDocx(TemplateVersion version, byte[] docxBytes) {
        List<DetectedVariable> detected = createFromDocxPreview(docxBytes);
        return persistDetected(version, detected);
    }

    public List<DetectedVariable> createFromDocxPreview(byte[] docxBytes) {
        return docxVariableParser.parse(docxBytes);
    }

    @Transactional
    public List<TemplateVariable> persistDetected(TemplateVersion version, List<DetectedVariable> detected) {
        List<TemplateVariable> entities = new ArrayList<>(detected.size());
        for (DetectedVariable item : detected) {
            TemplateVariable variable = new TemplateVariable();
            variable.setTemplateVersion(version);
            variable.setVariableKey(item.key());
            variable.setLabel(item.label());
            variable.setType(item.type());
            variable.setRequired(true);
            variable.setDisplayOrder(item.displayOrder());
            entities.add(variable);
        }
        return templateVariableRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public List<TemplateVariableResponse> list(DocuForgePrincipal principal, UUID versionId) {
        requireVersion(principal.getCompanyId(), versionId);
        return templateVariableRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(versionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<TemplateVariableResponse> update(
            DocuForgePrincipal principal,
            UUID versionId,
            TemplateVariablesUpdateRequest request
    ) {
        TemplateVersion version = requireVersion(principal.getCompanyId(), versionId);
        List<TemplateVariable> existing = templateVariableRepository
                .findByTemplateVersionIdOrderByDisplayOrderAsc(versionId);
        Map<String, TemplateVariable> byKey = existing.stream()
                .collect(Collectors.toMap(TemplateVariable::getVariableKey, Function.identity()));

        Set<String> seen = new HashSet<>();
        for (TemplateVariableUpdateItem item : request.variables()) {
            if (!VariableKeyRules.isValid(item.key())) {
                throw badRequest("Cle de variable invalide: " + item.key());
            }
            if (!seen.add(item.key())) {
                throw conflict("Cle de variable en double dans la requete: " + item.key());
            }
            TemplateVariable variable = byKey.get(item.key());
            if (variable == null) {
                throw badRequest("Variable inconnue pour cette version: " + item.key());
            }
            variable.setLabel(item.label());
            variable.setType(item.type());
            variable.setRequired(item.required());
            variable.setDefaultValue(item.defaultValue());
            variable.setPlaceholder(item.placeholder());
            if (item.displayOrder() != null) {
                variable.setDisplayOrder(item.displayOrder());
            }
            variable.setConfiguration(item.configuration());
        }

        if (seen.size() != existing.size()) {
            throw badRequest("Toutes les variables detectees doivent etre fournies dans la mise a jour.");
        }

        List<TemplateVariable> saved = templateVariableRepository.saveAll(existing);
        writeAudit(principal, AuditActions.TEMPLATE_VARIABLES_UPDATED, version.getTemplate().getId());
        return saved.stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(this::toResponse)
                .toList();
    }

    private TemplateVersion requireVersion(UUID companyId, UUID versionId) {
        return templateVersionRepository.findByIdAndCompanyId(versionId, companyId)
                .orElseThrow(() -> notFound("Version de template introuvable"));
    }

    private TemplateVariableResponse toResponse(TemplateVariable variable) {
        return new TemplateVariableResponse(
                variable.getId(),
                variable.getVariableKey(),
                variable.getLabel(),
                variable.getType(),
                variable.isRequired(),
                variable.getDefaultValue(),
                variable.getPlaceholder(),
                variable.getDisplayOrder(),
                variable.getConfiguration(),
                variable.getCreatedAt()
        );
    }

    private void writeAudit(DocuForgePrincipal principal, String action, UUID entityId) {
        auditService.recordSuccess(principal, action, "TEMPLATE", entityId);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}