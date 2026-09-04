package ai.docuforge.ai;

public record AIRequest(
        AiOperation operation,
        String text,
        String instruction,
        String context
) {
}
