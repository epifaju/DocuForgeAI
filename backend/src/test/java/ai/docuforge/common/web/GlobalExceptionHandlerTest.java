package ai.docuforge.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.docuforge.businesspack.archive.PackArchiveException;
import ai.docuforge.businesspack.manifest.PackManifestException;
import ai.docuforge.common.api.ErrorResponse;
import ai.docuforge.common.api.ErrorResponse.FieldErrorDetail;
import ai.docuforge.common.i18n.ErrorMessages;
import ai.docuforge.form.FormValidationException;
import ai.docuforge.storage.StorageException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        ErrorMessages errorMessages = mock(ErrorMessages.class);
        when(errorMessages.localize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        handler = new GlobalExceptionHandler(errorMessages);
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "trace-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void mapsResponseStatusCodes() {
        assertCode(handler.handleStatus(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "x")), "UNAUTHORIZED");
        assertCode(handler.handleStatus(new ResponseStatusException(HttpStatus.FORBIDDEN, "x")), "FORBIDDEN");
        assertCode(handler.handleStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "x")), "NOT_FOUND");
        assertCode(handler.handleStatus(new ResponseStatusException(HttpStatus.CONFLICT, "x")), "CONFLICT");
        assertCode(handler.handleStatus(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "x")), "INVALID_FORM_DATA");
        assertCode(handler.handleStatus(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "x")), "RATE_LIMITED");
        assertCode(handler.handleStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST, "x")), "REQUEST_ERROR");
    }

    @Test
    void mapsFormValidationAndStorageAndSecurityExceptions() {
        ResponseEntity<ErrorResponse> form = handler.handleFormValidation(
                new FormValidationException("error.form.invalid", List.of(new FieldErrorDetail("a", "error.form.required")))
        );
        assertThat(form.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(form.getBody().code()).isEqualTo("INVALID_FORM_DATA");
        assertThat(form.getBody().details()).hasSize(1);

        ResponseEntity<ErrorResponse> storage = handler.handleStorage(
                new StorageException("MALWARE_DETECTED", HttpStatus.UNPROCESSABLE_ENTITY, "error.antivirus.malware")
        );
        assertThat(storage.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(storage.getBody().code()).isEqualTo("MALWARE_DETECTED");

        assertCode(handler.handleAccessDenied(new AccessDeniedException("no")), "FORBIDDEN");
        assertCode(handler.handleAuthentication(new BadCredentialsException("error.auth.required")), "UNAUTHORIZED");
    }

    @Test
    void genericErrorIncludesTraceId() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(new RuntimeException("boom"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().traceId()).isEqualTo("trace-123");
    }

    @Test
    void mapsValidationConstraintAndPackExceptions() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "email", "error.validation.email"));
        MethodArgumentNotValidException manve = new MethodArgumentNotValidException(null, binding);
        ResponseEntity<ErrorResponse> validation = handler.handleValidation(manve);
        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(validation.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(validation.getBody().details()).isNotEmpty();

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("size");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("error.validation.size");
        ResponseEntity<ErrorResponse> constraint =
                handler.handleConstraint(new ConstraintViolationException(Set.of(violation)));
        assertThat(constraint.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(constraint.getBody().details()).hasSize(1);

        ResponseEntity<ErrorResponse> archive = handler.handlePackArchive(
                new PackArchiveException("PACK_ARCHIVE_INVALID", HttpStatus.BAD_REQUEST, "error.pack.archive_invalid")
        );
        assertThat(archive.getBody().code()).isEqualTo("PACK_ARCHIVE_INVALID");

        ResponseEntity<ErrorResponse> manifest = handler.handlePackManifest(
                new PackManifestException("PACK_MANIFEST_INVALID", HttpStatus.BAD_REQUEST, "error.pack.manifest_invalid")
        );
        assertThat(manifest.getBody().code()).isEqualTo("PACK_MANIFEST_INVALID");

        assertThat(handler.handleStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST))
                .getBody()
                .message()).isEqualTo("error.internal");
    }

    private static void assertCode(ResponseEntity<ErrorResponse> response, String code) {
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().traceId()).isEqualTo("trace-123");
    }
}
