package ai.docuforge.audit;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.audit.dto.AuditLogResponse;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.common.api.PageResponse;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(auditQueryService.list(
                principal, action, entityType, status, createdFrom, createdTo, page, size
        ));
    }
}
