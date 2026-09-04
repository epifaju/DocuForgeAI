package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int loginPerMinute,
        int aiPerMinute
) {
}
