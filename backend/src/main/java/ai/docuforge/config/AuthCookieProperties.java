package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.auth.cookies")
public record AuthCookieProperties(
        boolean enabled,
        String accessCookieName,
        String refreshCookieName,
        String sameSite,
        boolean secure,
        String path
) {
    public AuthCookieProperties {
        if (accessCookieName == null || accessCookieName.isBlank()) {
            accessCookieName = "df_access";
        }
        if (refreshCookieName == null || refreshCookieName.isBlank()) {
            refreshCookieName = "df_refresh";
        }
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Lax";
        }
        if (path == null || path.isBlank()) {
            path = "/";
        }
    }
}
