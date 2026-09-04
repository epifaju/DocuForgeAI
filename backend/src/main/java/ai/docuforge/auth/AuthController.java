package ai.docuforge.auth;

import ai.docuforge.auth.dto.LoginRequest;
import ai.docuforge.auth.dto.LogoutRequest;
import ai.docuforge.auth.dto.MeResponse;
import ai.docuforge.auth.dto.RefreshRequest;
import ai.docuforge.auth.dto.TokenResponse;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(principal.getUserId(), request.refreshToken());
        return ApiResponse.ok(null, "Déconnexion effectuée");
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal DocuForgePrincipal principal) {
        return ApiResponse.ok(authService.me(principal.getUserId()));
    }
}