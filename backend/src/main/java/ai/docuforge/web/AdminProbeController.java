package ai.docuforge.web;

import ai.docuforge.common.api.ApiResponse;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal RBAC probe endpoint used by Phase 4 security tests.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminProbeController {

    @GetMapping("/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("scope", "ADMIN"));
    }
}