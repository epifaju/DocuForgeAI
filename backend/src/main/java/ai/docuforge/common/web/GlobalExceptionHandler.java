package ai.docuforge.common.web;

import ai.docuforge.common.api.ErrorResponse;
import ai.docuforge.common.api.ErrorResponse.FieldErrorDetail;
import ai.docuforge.common.i18n.ErrorMessages;
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

    private final ErrorMessages errorMessages;

    public GlobalExceptionHandler(ErrorMessages errorMessages) {
        this.errorMessages = errorMessages;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                errorMessages.localize("error.validation"),
                details
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(
                        v.getPropertyPath().toString(),
                        errorMessages.localize(v.getMessage())
                ))
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                errorMessages.localize("error.validation"),
                details
        );
    }

    @ExceptionHandler(FormValidationException.class)
    public ResponseEntity<ErrorResponse> handleFormValidation(FormValidationException ex) {
        List<FieldErrorDetail> details = ex.getDetails().stream()
                .map(d -> new FieldErrorDetail(d.field(), errorMessages.localize(d.message())))
                .toList();
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_FORM_DATA",
                errorMessages.localize(ex.getMessage()),
                details
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", errorMessages.localize("error.forbidden"), List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        String raw = ex.getMessage() == null ? "error.auth.required" : ex.getMessage();
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", errorMessages.localize(raw), List.of());
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex) {
        return build(ex.getStatus(), ex.getCode(), errorMessages.localize(ex.getMessage()), List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED"
                : status == HttpStatus.FORBIDDEN ? "FORBIDDEN"
                : status == HttpStatus.NOT_FOUND ? "NOT_FOUND"
                : status == HttpStatus.CONFLICT ? "CONFLICT"
                : status == HttpStatus.UNPROCESSABLE_ENTITY ? "INVALID_FORM_DATA"
                : status == HttpStatus.TOO_MANY_REQUESTS ? "RATE_LIMITED"
                : "REQUEST_ERROR";
        String raw = ex.getReason() == null ? "error.internal" : ex.getReason();
        return build(status, code, errorMessages.localize(raw), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                errorMessages.localize("error.internal"),
                List.of()
        );
    }

    private FieldErrorDetail toDetail(FieldError error) {
        String message = error.getDefaultMessage() == null
                ? errorMessages.localize("error.validation")
                : errorMessages.localize(error.getDefaultMessage());
        return new FieldErrorDetail(error.getField(), message);
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
