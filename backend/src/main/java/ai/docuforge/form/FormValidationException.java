package ai.docuforge.form;

import ai.docuforge.common.api.ErrorResponse.FieldErrorDetail;
import java.util.List;

/**
 * PRD §85 — invalid form data must not proceed; returned as HTTP 422.
 */
public class FormValidationException extends RuntimeException {

    private final List<FieldErrorDetail> details;

    public FormValidationException(String message, List<FieldErrorDetail> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<FieldErrorDetail> getDetails() {
        return details;
    }
}