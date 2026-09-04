package ai.docuforge.batch;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.batch.dto.BatchErrorItem;
import ai.docuforge.batch.dto.BatchJobResponse;
import ai.docuforge.common.api.ApiResponse;
import ai.docuforge.common.api.PageResponse;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<BatchJobResponse> create(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam UUID templateId,
            @RequestParam(required = false) UUID templateVersionId,
            @RequestParam(required = false) String mapping
    ) {
        return ApiResponse.ok(
                batchService.create(principal, templateId, templateVersionId, mapping, file),
                "Batch accepte"
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<PageResponse<BatchJobResponse>> list(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(batchService.list(principal, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<BatchJobResponse> get(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(batchService.get(principal, id));
    }

    @GetMapping("/{id}/errors")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<List<BatchErrorItem>> errors(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(batchService.errors(principal, id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        BatchService.Download download = batchService.downloadZip(principal, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.body());
    }

    @GetMapping("/{id}/errors/download")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ResponseEntity<org.springframework.core.io.Resource> downloadErrors(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @PathVariable UUID id
    ) {
        BatchService.Download download = batchService.downloadErrors(principal, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.body());
    }
}
