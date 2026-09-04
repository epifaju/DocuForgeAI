package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.jwt")
public record JwtProperties(
        String secret,
        long accessExpirationSeconds,
        long refreshExpirationSeconds
) {
}