package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.mail")
public record MailProperties(
        boolean enabled,
        String from,
        long maxAttachmentBytes
) {
}
