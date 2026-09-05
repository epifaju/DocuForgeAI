package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int loginPerMinute,
        int aiPerMinute,
        int forgotPasswordPerMinute
) {
    public RateLimitProperties {
        if (forgotPasswordPerMinute <= 0) {
            forgotPasswordPerMinute = 5;
        }
    }
}
