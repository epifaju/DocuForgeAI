package ai.docuforge.common.web;

import ai.docuforge.common.api.ErrorResponse;
import ai.docuforge.common.api.ErrorResponse.FieldErrorDetail;
import ai.docuforge.form.FormValidationException;
import ai.docuforge.storage.StorageException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Les donnees fournies sont invalides.", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Les donnees fournies sont invalides.", details);
    }

    @ExceptionHandler(FormValidationException.class)
    public ResponseEntity<ErrorResponse> handleFormValidation(FormValidationException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FORM_DATA", ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acces refuse.", List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        String message = ex.getMessage() == null ? "Authentification requise." : ex.getMessage();
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message, List.of());
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED"
                : status == HttpStatus.FORBIDDEN ? "FORBIDDEN"
                : status == HttpStatus.NOT_FOUND ? "NOT_FOUND"
                : status == HttpStatus.CONFLICT ? "CONFLICT"
                : status == HttpStatus.UNPROCESSABLE_ENTITY ? "INVALID_FORM_DATA"
                : "REQUEST_ERROR";
        String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
        return build(status, code, message, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Une erreur interne est survenue.",
                List.of()
        );
    }

    private FieldErrorDetail toDetail(FieldError error) {
        return new FieldErrorDetail(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            List<FieldErrorDetail> details
    ) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                details,
                MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)
        );
        return ResponseEntity.status(status).body(body);
    }
}
