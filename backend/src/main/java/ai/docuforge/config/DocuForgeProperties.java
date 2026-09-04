package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge")
public record DocuForgeProperties(
        String appEnv,
        String appBaseUrl,
        String timezone
) {
}