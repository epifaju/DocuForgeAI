package ai.docuforge.domain.businesspack;

public enum PackImportJobStatus {
    UPLOADED,
    SCANNING,
    VALIDATING,
    VALID,
    INVALID,
    INSTALLING,
    INSTALLED,
    FAILED,
    EXPIRED
}
