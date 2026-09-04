package ai.docuforge.domain.batch;

public enum BatchJobStatus {
    CREATED,
    VALIDATING,
    PROCESSING,
    COMPLETED,
    PARTIALLY_FAILED,
    FAILED
}