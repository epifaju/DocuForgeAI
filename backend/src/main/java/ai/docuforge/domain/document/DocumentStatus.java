package ai.docuforge.domain.document;

public enum DocumentStatus {
    PENDING,
    VALIDATING,
    GENERATING,
    GENERATED,
    CONVERTING,
    COMPLETED,
    FAILED,
    REVIEW_REQUIRED
}