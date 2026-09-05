package ai.docuforge.auth;

import ai.docuforge.config.AuthCookieProperties;
import ai.docuforge.config.DocuForgeProperties;
import ai.docuforge.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

    private final AuthCookieProperties properties;
    private final JwtProperties jwtProperties;
    private final DocuForgeProperties docuForgeProperties;

    public AuthCookieService(
            AuthCookieProperties properties,
            JwtProperties jwtProperties,
            DocuForgeProperties docuForgeProperties
    ) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
        this.docuForgeProperties = docuForgeProperties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public void writeTokens(HttpServletResponse response, String accessToken, String refreshToken) {
        if (!properties.enabled()) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(accessToken, jwtProperties.accessExpirationSeconds()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, jwtProperties.refreshExpirationSeconds()).toString());
    }

    public void clear(HttpServletResponse response) {
        if (!properties.enabled()) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie("", 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString());
    }

    public Optional<String> readAccess(HttpServletRequest request) {
        return read(request, properties.accessCookieName());
    }

    public Optional<String> readRefresh(HttpServletRequest request) {
        return read(request, properties.refreshCookieName());
    }

    private Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private ResponseCookie accessCookie(String value, long maxAgeSeconds) {
        return base(properties.accessCookieName(), value, maxAgeSeconds).build();
    }

    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return base(properties.refreshCookieName(), value, maxAgeSeconds)
                .path("/api/v1/auth")
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, long maxAgeSeconds) {
        boolean secure = properties.secure() || isProduction();
        return ResponseCookie.from(name, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .path(properties.path())
                .maxAge(Math.max(0, maxAgeSeconds))
                .sameSite(properties.sameSite());
    }

    private boolean isProduction() {
        String env = docuForgeProperties.appEnv();
        return env != null && (env.equalsIgnoreCase("production") || env.equalsIgnoreCase("prod"));
    }
}
