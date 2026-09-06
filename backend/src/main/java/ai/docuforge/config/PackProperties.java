package ai.docuforge.config;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuforge.packs")
public record PackProperties(
        boolean enabled,
        int maxUploadSizeMb,
        int maxUncompressedSizeMb,
        int maxFiles,
        int maxTemplates,
        int maxSingleFileMb,
        int maxCompressionRatio,
        int importRetentionHours,
        String platformVersion,
        Set<String> allowedExtensions
) {
    private static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            "json", "docx", "txt", "md", "csv", "png", "jpg", "jpeg", "webp"
    );

    public PackProperties {
        if (maxUploadSizeMb <= 0) {
            maxUploadSizeMb = 100;
        }
        if (maxUncompressedSizeMb <= 0) {
            maxUncompressedSizeMb = 250;
        }
        if (maxFiles <= 0) {
            maxFiles = 500;
        }
        if (maxTemplates <= 0) {
            maxTemplates = 100;
        }
        if (maxSingleFileMb <= 0) {
            maxSingleFileMb = 25;
        }
        if (maxCompressionRatio <= 0) {
            maxCompressionRatio = 50;
        }
        if (importRetentionHours <= 0) {
            importRetentionHours = 24;
        }
        if (platformVersion == null || platformVersion.isBlank()) {
            platformVersion = "0.1.0";
        } else {
            platformVersion = platformVersion.trim();
        }
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            allowedExtensions = DEFAULT_EXTENSIONS;
        } else {
            allowedExtensions = allowedExtensions.stream()
                    .map(ext -> ext.toLowerCase(Locale.ROOT).replace(".", ""))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public long maxUploadBytes() {
        return maxUploadSizeMb * 1024L * 1024L;
    }

    public long maxUncompressedBytes() {
        return maxUncompressedSizeMb * 1024L * 1024L;
    }

    public long maxSingleFileBytes() {
        return maxSingleFileMb * 1024L * 1024L;
    }

    /**
     * SemVer used for pack compatibility (§70). Strips Maven {@code -SNAPSHOT} suffix.
     */
    public String normalizedPlatformVersion() {
        String value = platformVersion;
        int snap = value.toUpperCase(Locale.ROOT).indexOf("-SNAPSHOT");
        if (snap > 0) {
            return value.substring(0, snap);
        }
        return value;
    }
}
