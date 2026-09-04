package ai.docuforge.ai;

public record AIResponse(
        String content,
        String provider,
        String model,
        String promptVersion,
        long durationMs
) {
}
