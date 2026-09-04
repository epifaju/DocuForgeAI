package ai.docuforge.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.storage")
public record StorageProperties(
        String root,
        int maxUploadSizeMb,
        Set<String> allowedExtensions,
        Set<String> allowedContentTypes
) {
}