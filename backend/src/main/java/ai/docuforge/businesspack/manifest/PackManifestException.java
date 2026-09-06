package ai.docuforge.businesspack.manifest;

import org.springframework.http.HttpStatus;

public class PackManifestException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public PackManifestException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public PackManifestException(String code, HttpStatus status, String message, Throwable cause) {
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
