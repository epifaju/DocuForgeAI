package ai.docuforge.form;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.form.dto.FormDataValidateRequest;
import ai.docuforge.form.dto.FormDataValidateResponse;
import ai.docuforge.form.dto.FormSchemaResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/template-versions")
public class FormSchemaController {

    private final FormSchemaService formSchemaService;

    public FormSchemaController(FormSchemaService formSchemaService) {
        this.formSchemaService = formSchemaService;
    }

    @GetMapping("/{id}/form-schema")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<FormSchemaResponse> formSchema(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(formSchemaService.getSchema(principal, id));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ApiResponse<FormDataValidateResponse> validate(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody FormDataValidateRequest request
    ) {
        return ApiResponse.ok(formSchemaService.validate(principal, id, request), "Donnees valides");
    }
}