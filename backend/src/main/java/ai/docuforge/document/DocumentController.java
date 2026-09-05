package ai.docuforge.document;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.domain.document.DocumentStatus;
import ai.docuforge.document.dto.DocumentGenerateRequest;
import ai.docuforge.document.dto.DocumentNewVersionRequest;
import ai.docuforge.document.dto.GeneratedDocumentResponse;
import ai.docuforge.email.DocumentEmailService;
import ai.docuforge.email.dto.DocumentEmailRequest;
import ai.docuforge.email.dto.DocumentEmailResponse;
import ai.docuforge.privacy.GdprService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentGenerationService documentGenerationService;
    private final DocumentEmailService documentEmailService;
    private final GdprService gdprService;

    public DocumentController(
            DocumentGenerationService documentGenerationService,
            DocumentEmailService documentEmailService,
            GdprService gdprService
    ) {
        this.documentGenerationService = documentGenerationService;
        this.documentEmailService = documentEmailService;
        this.gdprService = gdprService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<PageResponse<GeneratedDocumentResponse>> list(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(documentGenerationService.list(
                principal, status, templateId, createdBy, createdFrom, createdTo, q, page, size
        ));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ApiResponse<GeneratedDocumentResponse> generate(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody DocumentGenerateRequest request
    ) {
        return ApiResponse.ok(documentGenerationService.generate(principal, request), "Document genere");
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ResponseEntity<byte[]> preview(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody DocumentGenerateRequest request
    ) {
        byte[] docx = documentGenerationService.preview(principal, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"preview.docx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<GeneratedDocumentResponse> get(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(documentGenerationService.get(principal, id));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<java.util.List<GeneratedDocumentResponse>> listVersions(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(documentGenerationService.listVersions(principal, id));
    }

    @PostMapping("/{id}/new-version")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GeneratedDocumentResponse> createNewVersion(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody DocumentNewVersionRequest request
    ) {
        return ApiResponse.ok(
                documentGenerationService.createNewVersion(principal, id, request),
                "Nouvelle version creee"
        );
    }

    @GetMapping("/{id}/download/docx")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ResponseEntity<org.springframework.core.io.Resource> downloadDocx(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        DocumentGenerationService.DocumentDownload download =
                documentGenerationService.downloadDocx(principal, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.body());
    }

    @GetMapping("/{id}/download/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ResponseEntity<org.springframework.core.io.Resource> downloadPdf(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean preview
    ) {
        DocumentGenerationService.DocumentDownload download =
                documentGenerationService.downloadPdf(principal, id, preview);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.body());
    }

    @PostMapping("/{id}/email")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ApiResponse<DocumentEmailResponse> email(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody DocumentEmailRequest request
    ) {
        return ApiResponse.ok(documentEmailService.send(principal, id, request), "Email envoye");
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        gdprService.deleteDocument(principal, id);
        return ApiResponse.ok(null, "error.privacy.document_deleted");
    }
}
