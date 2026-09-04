package ai.docuforge.batch.dto;

public record BatchErrorItem(
        int rowNumber,
        String message
) {
}
