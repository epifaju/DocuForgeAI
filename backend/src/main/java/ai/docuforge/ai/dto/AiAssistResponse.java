package ai.docuforge.ai.dto;

public record AiAssistResponse(
        String result,
        String operation,
        String provider,
        String model,
        String promptVersion,
        long durationMs
) {
}
