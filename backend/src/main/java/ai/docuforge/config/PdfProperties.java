package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.pdf")
public record PdfProperties(
        boolean enabled,
        int timeoutSeconds,
        String libreofficeHost,
        int libreofficePort,
        int maxRetries
) {
}