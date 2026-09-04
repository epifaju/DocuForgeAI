package ai.docuforge.template;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.template.dto.TemplateVariableResponse;
import ai.docuforge.template.dto.TemplateVariablesUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/template-versions")
public class TemplateVariableController {

    private final TemplateVariableService templateVariableService;

    public TemplateVariableController(TemplateVariableService templateVariableService) {
        this.templateVariableService = templateVariableService;
    }

    @GetMapping("/{id}/variables")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<List<TemplateVariableResponse>> list(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(templateVariableService.list(principal, id));
    }

    @PutMapping("/{id}/variables")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiResponse<List<TemplateVariableResponse>> update(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody TemplateVariablesUpdateRequest request
    ) {
        return ApiResponse.ok(templateVariableService.update(principal, id, request), "Variables mises a jour");
    }
}