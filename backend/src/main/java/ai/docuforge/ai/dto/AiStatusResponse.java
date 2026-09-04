package ai.docuforge.ai.dto;

public record AiStatusResponse(
        boolean enabled,
        String provider,
        String model
) {
}
