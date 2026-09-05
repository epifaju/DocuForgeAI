package ai.docuforge.privacy;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    private final GdprService gdprService;

    public PrivacyController(GdprService gdprService) {
        this.gdprService = gdprService;
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal DocuForgePrincipal principal) {
        byte[] zip = gdprService.exportUserData(principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"docuforge-data-export.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<Void> deleteAccount(@AuthenticationPrincipal DocuForgePrincipal principal) {
        gdprService.deleteOwnAccount(principal);
        return ApiResponse.ok(null, "error.privacy.account_deleted");
    }

    @PostMapping("/purge")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Integer>> purge(@AuthenticationPrincipal DocuForgePrincipal principal) {
        int deleted = gdprService.purgeExpiredDocuments(principal);
        return ApiResponse.ok(Map.of("deleted", deleted));
    }
}
