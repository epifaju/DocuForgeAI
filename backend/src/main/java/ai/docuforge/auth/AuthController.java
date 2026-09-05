package ai.docuforge.auth;

import ai.docuforge.auth.dto.ForgotPasswordRequest;
import ai.docuforge.auth.dto.LoginRequest;
import ai.docuforge.auth.dto.LogoutRequest;
import ai.docuforge.auth.dto.MeResponse;
import ai.docuforge.auth.dto.RefreshRequest;
import ai.docuforge.auth.dto.ResetPasswordRequest;
import ai.docuforge.auth.dto.TokenResponse;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthCookieService authCookieService;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            AuthCookieService authCookieService
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        TokenResponse tokens = authService.login(request, httpRequest.getRemoteAddr());
        authCookieService.writeTokens(httpResponse, tokens.accessToken(), tokens.refreshToken());
        return ApiResponse.ok(tokens);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String raw = resolveRefresh(request == null ? null : request.refreshToken(), httpRequest);
        TokenResponse tokens = authService.refresh(raw);
        authCookieService.writeTokens(httpResponse, tokens.accessToken(), tokens.refreshToken());
        return ApiResponse.ok(tokens);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal DocuForgePrincipal principal,
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String raw = resolveRefreshOptional(request == null ? null : request.refreshToken(), httpRequest);
        if (raw != null) {
            authService.logout(principal.getUserId(), raw);
        }
        authCookieService.clear(httpResponse);
        return ApiResponse.ok(null, "error.auth.logout_ok");
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal DocuForgePrincipal principal) {
        return ApiResponse.ok(authService.me(principal.getUserId()));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        passwordResetService.forgotPassword(request, httpRequest.getRemoteAddr());
        return ApiResponse.ok(null, "error.auth.forgot_accepted");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ApiResponse.ok(null, "error.auth.reset_ok");
    }

    private String resolveRefresh(String bodyToken, HttpServletRequest request) {
        if (bodyToken != null && !bodyToken.isBlank()) {
            return bodyToken.trim();
        }
        return authCookieService
                .readRefresh(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "error.auth.invalid_refresh"));
    }

    private String resolveRefreshOptional(String bodyToken, HttpServletRequest request) {
        if (bodyToken != null && !bodyToken.isBlank()) {
            return bodyToken.trim();
        }
        return authCookieService.readRefresh(request).orElse(null);
    }
}
