package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.antivirus")
public record AntivirusProperties(
        boolean enabled,
        String host,
        int port,
        int timeoutMs
) {
}
