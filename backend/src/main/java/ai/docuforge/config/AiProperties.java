package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String ollamaBaseUrl,
        String ollamaModel,
        int timeoutSeconds,
        int maxRetries
) {
}
