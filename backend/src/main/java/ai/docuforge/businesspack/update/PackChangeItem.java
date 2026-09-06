package ai.docuforge.businesspack.update;

/**
 * Single detected change between pack versions (PRD §91).
 */
public record PackChangeItem(
        String kind,
        String templateOrPromptCode,
        String variableKey,
        String before,
        String after
) {
}
