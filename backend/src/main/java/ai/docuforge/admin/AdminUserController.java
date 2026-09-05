package ai.docuforge.admin;

import ai.docuforge.admin.dto.AdminUserCreateRequest;
import ai.docuforge.admin.dto.AdminUserResponse;
import ai.docuforge.admin.dto.AdminUserUpdateRequest;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.ok(adminUserService.list(principal, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> get(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(adminUserService.get(principal, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminUserResponse> create(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody AdminUserCreateRequest request
    ) {
        return ApiResponse.ok(adminUserService.create(principal, request), "Utilisateur cree");
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminUserResponse> update(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserUpdateRequest request
    ) {
        return ApiResponse.ok(adminUserService.update(principal, id, request), "Utilisateur mis a jour");
    }
}
