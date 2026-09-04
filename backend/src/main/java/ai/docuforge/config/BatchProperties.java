package ai.docuforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.batch")
public record BatchProperties(int maxRows, boolean sync) {
}
