package ai.docuforge.businesspack.web;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.dto.PackImportJobResponse;
import ai.docuforge.businesspack.dto.PackInstallRequest;
import ai.docuforge.businesspack.dto.PackInstallResponse;
import ai.docuforge.businesspack.dto.PackUpdatePreviewResponse;
import ai.docuforge.businesspack.importjob.PackImportService;
import ai.docuforge.businesspack.installation.PackInstallationService;
import ai.docuforge.businesspack.update.PackUpdateService;
import ai.docuforge.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ADMIN pack import / install / update-preview API (PRD §§78–82, §§90–93).
 */
@RestController
@RequestMapping("/api/v1/admin/business-packs")
@PreAuthorize("hasRole('ADMIN')")
public class PackImportController {

    private final PackImportService packImportService;
    private final PackInstallationService packInstallationService;
    private final PackUpdateService packUpdateService;

    public PackImportController(
            PackImportService packImportService,
            PackInstallationService packInstallationService,
            PackUpdateService packUpdateService
    ) {
        this.packImportService = packImportService;
        this.packInstallationService = packInstallationService;
        this.packUpdateService = packUpdateService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<PackImportJobResponse> importPack(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(packImportService.upload(principal, file), "Pack importe en staging");
    }

    @GetMapping("/imports/{jobId}")
    public ApiResponse<PackImportJobResponse> getImport(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID jobId
    ) {
        return ApiResponse.ok(packImportService.get(principal, jobId));
    }

    @PostMapping("/imports/{jobId}/validate")
    public ApiResponse<PackImportJobResponse> validateImport(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID jobId
    ) {
        return ApiResponse.ok(packImportService.validate(principal, jobId), "Validation terminee");
    }

    @GetMapping("/imports/{jobId}/update-preview")
    public ApiResponse<PackUpdatePreviewResponse> updatePreview(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID jobId
    ) {
        return ApiResponse.ok(packUpdateService.preview(principal, jobId));
    }

    @PostMapping("/imports/{jobId}/install")
    public ApiResponse<PackInstallResponse> installImport(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID jobId,
            @Valid @RequestBody(required = false) PackInstallRequest request
    ) {
        return ApiResponse.ok(
                packInstallationService.install(principal, jobId, request),
                "Pack installe"
        );
    }
}
