package ai.docuforge.form;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.domain.template.VariableType;
import ai.docuforge.form.dto.FormDataValidateRequest;
import ai.docuforge.form.dto.FormDataValidateResponse;
import ai.docuforge.form.dto.FormFieldConstraints;
import ai.docuforge.form.dto.FormFieldSchema;
import ai.docuforge.form.dto.FormSchemaResponse;
import ai.docuforge.template.parser.DocxVariableParser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FormSchemaService {

    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateVariableRepository templateVariableRepository;
    private final FormFieldConstraintsParser constraintsParser;
    private final FormDataValidator formDataValidator;

    public FormSchemaService(
            TemplateVersionRepository templateVersionRepository,
            TemplateVariableRepository templateVariableRepository,
            FormFieldConstraintsParser constraintsParser,
            FormDataValidator formDataValidator
    ) {
        this.templateVersionRepository = templateVersionRepository;
        this.templateVariableRepository = templateVariableRepository;
        this.constraintsParser = constraintsParser;
        this.formDataValidator = formDataValidator;
    }

    @Transactional(readOnly = true)
    public FormSchemaResponse getSchema(DocuForgePrincipal principal, UUID versionId) {
        TemplateVersion version = requireVersion(principal.getCompanyId(), versionId);
        Template template = version.getTemplate();
        List<FormFieldSchema> fields = templateVariableRepository
                .findByTemplateVersionIdOrderByDisplayOrderAsc(versionId)
                .stream()
                .map(this::toField)
                .toList();
        return new FormSchemaResponse(
                version.getId(),
                template.getId(),
                template.getCode(),
                template.getName(),
                version.getVersionNumber(),
                fields
        );
    }

    @Transactional(readOnly = true)
    public FormDataValidateResponse validate(
            DocuForgePrincipal principal,
            UUID versionId,
            FormDataValidateRequest request
    ) {
        requireVersion(principal.getCompanyId(), versionId);
        List<TemplateVariable> variables = templateVariableRepository
                .findByTemplateVersionIdOrderByDisplayOrderAsc(versionId);
        formDataValidator.validateOrThrow(variables, request.data());
        return new FormDataValidateResponse(true);
    }

    private FormFieldSchema toField(TemplateVariable variable) {
        VariableType type = DocxVariableParser.effectiveType(variable.getVariableKey(), variable.getType());
        FormFieldConstraints constraints = constraintsParser.parse(variable.getConfiguration());
        boolean aiEnabled = constraintsParser.aiEnabled(variable.getConfiguration(), type);
        String aiMode = constraintsParser.aiMode(variable.getConfiguration(), type);
        return new FormFieldSchema(
                variable.getVariableKey(),
                variable.getLabel(),
                type,
                variable.isRequired(),
                variable.getDefaultValue(),
                variable.getPlaceholder(),
                variable.getDisplayOrder(),
                componentFor(type),
                constraints,
                aiEnabled,
                aiMode
        );
    }

    static String componentFor(VariableType type) {
        return switch (type) {
            case LONG_TEXT -> "textarea";
            case NUMBER, DECIMAL, CURRENCY -> "number";
            case DATE -> "date";
            case DATETIME -> "datetime";
            case BOOLEAN -> "checkbox";
            case EMAIL -> "email";
            case PHONE -> "tel";
            case SELECT -> "select";
            case MULTISELECT -> "multiselect";
            case TEXT -> "text";
        };
    }

    private TemplateVersion requireVersion(UUID companyId, UUID versionId) {
        return templateVersionRepository.findByIdAndCompanyId(versionId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version de template introuvable"));
    }
}