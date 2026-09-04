package ai.docuforge.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AiException extends ResponseStatusException {

    public AiException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public AiException(HttpStatus status, String reason, Throwable cause) {
        super(status, reason, cause);
    }

    public static AiException disabled() {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE, "IA desactivee (AI_ENABLED=false).");
    }

    public static AiException unavailable(String message, Throwable cause) {
        return new AiException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
