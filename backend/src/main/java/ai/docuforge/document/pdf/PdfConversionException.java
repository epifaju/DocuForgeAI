package ai.docuforge.document.pdf;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PdfConversionException extends ResponseStatusException {

    public PdfConversionException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }

    public PdfConversionException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}