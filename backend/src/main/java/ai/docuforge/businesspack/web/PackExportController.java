package ai.docuforge.businesspack.web;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.export.PackExportDownload;
import ai.docuforge.businesspack.export.PackExportService;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN pack export as DBPF-1 ZIP (PRD §§97–98).
 */
@RestController
@RequestMapping("/api/v1/admin/business-packs")
@PreAuthorize("hasRole('ADMIN')")
public class PackExportController {

    private final PackExportService packExportService;

    public PackExportController(PackExportService packExportService) {
        this.packExportService = packExportService;
    }

    @GetMapping("/{packId}/export")
    public ResponseEntity<org.springframework.core.io.Resource> exportGet(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId
    ) {
        return toResponse(packExportService.exportPack(principal, packId));
    }

    @PostMapping("/{packId}/export")
    public ResponseEntity<org.springframework.core.io.Resource> exportPost(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID packId
    ) {
        return toResponse(packExportService.exportPack(principal, packId));
    }

    private static ResponseEntity<org.springframework.core.io.Resource> toResponse(PackExportDownload download) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contentLength())
                .body(download.body());
    }
}
