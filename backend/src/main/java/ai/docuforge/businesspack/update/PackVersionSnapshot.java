package ai.docuforge.businesspack.update;

import java.util.List;

/**
 * Immutable snapshot of a pack version used for change analysis.
 */
public record PackVersionSnapshot(
        String packKey,
        String version,
        List<TemplateSnapshot> templates,
        List<PromptSnapshot> prompts
) {
    public record TemplateSnapshot(
            String code,
            String name,
            String version,
            List<VariableSnapshot> variables
    ) {
    }

    public record VariableSnapshot(
            String key,
            String type,
            boolean required
    ) {
    }

    public record PromptSnapshot(
            String code,
            String version,
            String checksum
    ) {
    }
}
