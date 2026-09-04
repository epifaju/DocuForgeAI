package ai.docuforge.ai;

import ai.docuforge.ai.dto.AiAssistRequest;
import ai.docuforge.ai.dto.AiAssistResponse;
import ai.docuforge.ai.dto.AiStatusResponse;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER','VIEWER')")
    public ApiResponse<AiStatusResponse> status() {
        return ApiResponse.ok(aiService.status());
    }

    @PostMapping("/rewrite")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ApiResponse<AiAssistResponse> rewrite(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody AiAssistRequest request
    ) {
        return ApiResponse.ok(aiService.assist(principal, AiOperation.REWRITE, request), "Texte reecrit");
    }

    @PostMapping("/formalize")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ApiResponse<AiAssistResponse> formalize(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody AiAssistRequest request
    ) {
        return ApiResponse.ok(aiService.assist(principal, AiOperation.FORMALIZE, request), "Texte formalise");
    }

    @PostMapping("/summarize")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ApiResponse<AiAssistResponse> summarize(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody AiAssistRequest request
    ) {
        return ApiResponse.ok(aiService.assist(principal, AiOperation.SUMMARIZE, request), "Texte resume");
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','USER')")
    public ApiResponse<AiAssistResponse> generate(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody AiAssistRequest request
    ) {
        return ApiResponse.ok(
                aiService.assist(principal, AiOperation.GENERATE_PARAGRAPH, request),
                "Paragraphe genere"
        );
    }
}
