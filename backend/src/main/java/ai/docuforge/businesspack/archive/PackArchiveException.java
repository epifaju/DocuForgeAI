package ai.docuforge.businesspack.archive;

import org.springframework.http.HttpStatus;

/**
 * Archive security / structure failure for DBPF-1 ZIP inspection (Phase 4).
 */
public class PackArchiveException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public PackArchiveException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public PackArchiveException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
