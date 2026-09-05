package ai.docuforge.audit;

/**
 * Canonical sensitive actions (PRD §44). Extra operational events may also be recorded.
 */
public final class AuditActions {

    public static final String LOGIN = "LOGIN";
    public static final String TEMPLATE_CREATED = "TEMPLATE_CREATED";
    public static final String TEMPLATE_UPDATED = "TEMPLATE_UPDATED";
    public static final String TEMPLATE_ACTIVATED = "TEMPLATE_ACTIVATED";
    public static final String TEMPLATE_ARCHIVED = "TEMPLATE_ARCHIVED";
    public static final String TEMPLATE_DELETED = "TEMPLATE_DELETED";
    public static final String TEMPLATE_VERSION_CREATED = "TEMPLATE_VERSION_CREATED";
    public static final String TEMPLATE_VARIABLES_UPDATED = "TEMPLATE_VARIABLES_UPDATED";
    public static final String DOCUMENT_GENERATED = "DOCUMENT_GENERATED";
    public static final String DOCUMENT_DOWNLOADED = "DOCUMENT_DOWNLOADED";
    public static final String DOCUMENT_EMAILED = "DOCUMENT_EMAILED";
    public static final String DOCUMENT_VERSION_CREATED = "DOCUMENT_VERSION_CREATED";
    public static final String DOCUMENT_PDF_CONVERTED = "DOCUMENT_PDF_CONVERTED";
    public static final String DOCUMENT_PDF_FAILED = "DOCUMENT_PDF_FAILED";
    public static final String AI_REQUEST = "AI_REQUEST";
    public static final String BATCH_STARTED = "BATCH_STARTED";
    public static final String BATCH_COMPLETED = "BATCH_COMPLETED";
    public static final String SETTINGS_CHANGED = "SETTINGS_CHANGED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
    public static final String DATA_EXPORTED = "DATA_EXPORTED";
    public static final String DATA_PURGED = "DATA_PURGED";
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String DOCUMENT_DELETED = "DOCUMENT_DELETED";

    private AuditActions() {
    }
}
