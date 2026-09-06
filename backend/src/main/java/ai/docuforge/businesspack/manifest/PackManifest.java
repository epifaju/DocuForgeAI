package ai.docuforge.businesspack.manifest;

import ai.docuforge.domain.businesspack.BusinessPackType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * DBPF-1 root manifest DTO (Jackson). Unknown properties ignored at bind time;
 * {@link PackManifestValidator} emits warnings for unexpected top-level keys.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PackManifest(
        String schemaVersion,
        String id,
        String name,
        String slug,
        String version,
        BusinessPackType type,
        String description,
        Publisher publisher,
        Compatibility compatibility,
        List<String> locales,
        String defaultLocale,
        List<String> categories,
        List<String> tags,
        List<TemplateEntry> templates,
        List<PromptEntry> prompts,
        List<SampleEntry> samples,
        Map<String, String> checksums
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Publisher(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Compatibility(String minimumDocuForgeVersion, String maximumDocuForgeVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TemplateEntry(
            String code,
            String name,
            String version,
            String templateFile,
            String metadataFile,
            String previewFile,
            Boolean enabledByDefault
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromptEntry(String code, String version, String file) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SampleEntry(String code, String type, String file, String templateCode) {
    }
}
