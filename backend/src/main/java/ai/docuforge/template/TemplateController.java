package ai.docuforge.template;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.template.dto.TemplateCreateRequest;
import ai.docuforge.template.dto.TemplateDuplicateRequest;
import ai.docuforge.template.dto.TemplateResponse;
import ai.docuforge.template.dto.TemplateUpdateRequest;
import ai.docuforge.template.dto.TemplateVersionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<PageResponse<TemplateResponse>> list(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestParam(required = false) TemplateStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(templateService.list(principal, status, q, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateResponse> create(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody TemplateCreateRequest request
    ) {
        return ApiResponse.ok(templateService.create(principal, request), "Template créé");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<TemplateResponse> get(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(templateService.get(principal, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiResponse<TemplateResponse> update(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody TemplateUpdateRequest request
    ) {
        return ApiResponse.ok(templateService.update(principal, id, request), "Template mis à jour");
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateResponse> duplicate(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody TemplateDuplicateRequest request
    ) {
        return ApiResponse.ok(templateService.duplicate(principal, id, request), "Template duplique");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        templateService.delete(principal, id);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateVersionResponse> addVersion(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean setAsCurrent
    ) {
        return ApiResponse.ok(templateService.addVersion(principal, id, file, setAsCurrent), "Version ajoutée");
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<List<TemplateVersionResponse>> listVersions(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(templateService.listVersions(principal, id));
    }

    @GetMapping("/{id}/versions/{version}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<TemplateVersionResponse> getVersion(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @PathVariable int version
    ) {
        return ApiResponse.ok(templateService.getVersion(principal, id, version));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiResponse<TemplateResponse> activate(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(templateService.activate(principal, id), "Template activé");
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiResponse<TemplateResponse> archive(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(templateService.archive(principal, id), "Template archivé");
    }
}