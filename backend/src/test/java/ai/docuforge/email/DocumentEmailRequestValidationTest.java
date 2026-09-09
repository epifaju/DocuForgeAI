package ai.docuforge.email;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docuforge.domain.email.AttachmentFormat;
import ai.docuforge.email.dto.DocumentEmailRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentEmailRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsValidRequest() {
        Set<ConstraintViolation<DocumentEmailRequest>> violations = validator.validate(
                new DocumentEmailRequest("a@b.com", "Sujet", "msg", AttachmentFormat.DOCX, true)
        );
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsInvalidRecipientAndBlankSubject() {
        assertThat(validator.validate(
                new DocumentEmailRequest("not-an-email", "Sujet", null, AttachmentFormat.DOCX, true)
        )).isNotEmpty();

        assertThat(validator.validate(
                new DocumentEmailRequest("a@b.com", "  ", null, AttachmentFormat.DOCX, true)
        )).isNotEmpty();
    }

    @Test
    void rejectsUnconfirmedAndNullFormat() {
        assertThat(validator.validate(
                new DocumentEmailRequest("a@b.com", "Sujet", null, AttachmentFormat.DOCX, false)
        )).anyMatch(v -> v.getPropertyPath().toString().equals("confirmed"));

        assertThat(validator.validate(
                new DocumentEmailRequest("a@b.com", "Sujet", null, null, true)
        )).anyMatch(v -> v.getPropertyPath().toString().equals("attachmentFormat"));
    }

    @Test
    void rejectsOversizedFields() {
        String longRecipient = "a@" + "b".repeat(260) + ".com";
        assertThat(validator.validate(
                new DocumentEmailRequest(longRecipient, "Sujet", null, AttachmentFormat.DOCX, true)
        )).isNotEmpty();

        assertThat(validator.validate(
                new DocumentEmailRequest("a@b.com", "x".repeat(256), null, AttachmentFormat.DOCX, true)
        )).isNotEmpty();

        assertThat(validator.validate(
                new DocumentEmailRequest("a@b.com", "Sujet", "m".repeat(5001), AttachmentFormat.DOCX, true)
        )).isNotEmpty();
    }
}
