package ai.docuforge.dashboard;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.dashboard.dto.DashboardResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<DashboardResponse> get(@AuthenticationPrincipal DocuForgePrincipal principal) {
        return ApiResponse.ok(dashboardService.get(principal));
    }
}
