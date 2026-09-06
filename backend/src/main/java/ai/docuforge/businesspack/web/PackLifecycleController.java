package ai.docuforge.businesspack.web;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.dto.PackSummaryResponse;
import ai.docuforge.businesspack.dto.PackTemplateResponse;
import ai.docuforge.businesspack.dto.PackUninstallResponse;
import ai.docuforge.businesspack.lifecycle.PackLifecycleService;
import ai.docuforge.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN pack / template enable-disable and soft uninstall (PRD §§87–89, §§94–96).
 */
@RestController
@RequestMapping("/api/v1/admin/business-packs")
@PreAuthorize("hasRole('ADMIN')")
public class PackLifecycleController {

    private final PackLifecycleService packLifecycleService;

    public PackLifecycleController(PackLifecycleService packLifecycleService) {
        this.packLifecycleService = packLifecycleService;
    }

    @PostMapping("/{packId}/enable")
    public ApiResponse<PackSummaryResponse> enablePack(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId
    ) {
        return ApiResponse.ok(packLifecycleService.enablePack(principal, packId), "Pack active");
    }

    @PostMapping("/{packId}/disable")
    public ApiResponse<PackSummaryResponse> disablePack(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId
    ) {
        return ApiResponse.ok(packLifecycleService.disablePack(principal, packId), "Pack desactive");
    }

    @DeleteMapping("/{packId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<PackUninstallResponse> uninstallPack(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId
    ) {
        return ApiResponse.ok(
                packLifecycleService.uninstallPack(principal, packId),
                "Pack desinstalle"
        );
    }

    @PostMapping("/{packId}/templates/{templateCode}/enable")
    public ApiResponse<PackTemplateResponse> enableTemplate(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId,
            @PathVariable String templateCode
    ) {
        return ApiResponse.ok(
                packLifecycleService.enableTemplate(principal, packId, templateCode),
                "Template pack active"
        );
    }

    @PostMapping("/{packId}/templates/{templateCode}/disable")
    public ApiResponse<PackTemplateResponse> disableTemplate(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId,
            @PathVariable String templateCode
    ) {
        return ApiResponse.ok(
                packLifecycleService.disableTemplate(principal, packId, templateCode),
                "Template pack desactive"
        );
    }
}
