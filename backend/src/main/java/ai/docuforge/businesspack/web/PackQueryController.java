package ai.docuforge.businesspack.web;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.dto.PackDetailResponse;
import ai.docuforge.businesspack.dto.PackSummaryResponse;
import ai.docuforge.businesspack.dto.PackTemplateResponse;
import ai.docuforge.businesspack.dto.PackVersionResponse;
import ai.docuforge.businesspack.query.PackQueryService;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.BusinessPackType;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN pack query API (PRD §§83–86). Shares base path with {@link PackImportController}.
 */
@RestController
@RequestMapping("/api/v1/admin/business-packs")
@PreAuthorize("hasRole('ADMIN')")
public class PackQueryController {

    private final PackQueryService packQueryService;

    public PackQueryController(PackQueryService packQueryService) {
        this.packQueryService = packQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PackSummaryResponse>> list(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestParam(required = false) BusinessPackStatus status,
            @RequestParam(required = false) BusinessPackType type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return ApiResponse.ok(packQueryService.list(principal, status, type, search, page, size, sort));
    }

    @GetMapping("/{packId}")
    public ApiResponse<PackDetailResponse> get(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId
    ) {
        return ApiResponse.ok(packQueryService.get(principal, packId));
    }

    @GetMapping("/{packId}/versions")
    public ApiResponse<List<PackVersionResponse>> versions(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId
    ) {
        return ApiResponse.ok(packQueryService.listVersions(principal, packId));
    }

    @GetMapping("/{packId}/templates")
    public ApiResponse<List<PackTemplateResponse>> templates(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId
    ) {
        return ApiResponse.ok(packQueryService.listTemplates(principal, packId));
    }
}
