package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.bootstrap")
public record BootstrapProperties(
        boolean enabled,
        String companyName,
        String companyIdentifier,
        String adminEmail,
        String adminPassword,
        String adminFirstName,
        String adminLastName
) {
}