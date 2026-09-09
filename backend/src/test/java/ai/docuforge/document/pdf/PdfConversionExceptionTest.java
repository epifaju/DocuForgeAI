package ai.docuforge.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PdfConversionExceptionTest {

    @Test
    void mapsToServiceUnavailable() {
        PdfConversionException ex = new PdfConversionException("conversion failed");
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getReason()).isEqualTo("conversion failed");

        PdfConversionException withCause = new PdfConversionException("boom", new IllegalStateException("x"));
        assertThat(withCause.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(withCause.getCause()).isInstanceOf(IllegalStateException.class);
    }
}
