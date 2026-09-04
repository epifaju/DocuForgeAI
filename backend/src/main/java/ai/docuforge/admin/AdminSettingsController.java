package ai.docuforge.admin;

import ai.docuforge.admin.dto.AdminSettingsResponse;
import ai.docuforge.admin.dto.AdminSettingsUpdateRequest;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    public AdminSettingsController(AdminSettingsService adminSettingsService) {
        this.adminSettingsService = adminSettingsService;
    }

    @GetMapping
    public ApiResponse<AdminSettingsResponse> get(@AuthenticationPrincipal DocuForgePrincipal principal) {
        return ApiResponse.ok(adminSettingsService.get(principal));
    }

    @PutMapping
    public ApiResponse<AdminSettingsResponse> update(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody AdminSettingsUpdateRequest request
    ) {
        return ApiResponse.ok(adminSettingsService.update(principal, request), "Parametres enregistres");
    }
}
