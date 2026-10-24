package ai.docuforge.document.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docuforge.config.PdfProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JodConverterPdfConverterTest {

    private static final byte[] MINIMAL_PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path tempDir;

    @Mock
    OfficeManager officeManager;

    private Path sourceDocx;

    @BeforeEach
    void setUp() throws Exception {
        sourceDocx = tempDir.resolve("letter.docx");
        Files.writeString(sourceDocx, "docx-bytes");
    }

    @Test
    void convert_rejectsNullSource() {
        JodConverterPdfConverter converter = newConverter(0);
        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sourceDocument");
    }

    @Test
    void convert_rejectsMissingSourceFile() {
        JodConverterPdfConverter converter = newConverter(0);
        Path missing = tempDir.resolve("missing.docx");
        assertThatThrownBy(() -> converter.convert(missing))
                .isInstanceOf(PdfConversionException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void convert_succeedsOnFirstAttempt() throws Exception {
        RecordingConverter converter = new RecordingConverter(props(0), officeManager, 0);
        Path pdf = converter.convert(sourceDocx);
        assertThat(pdf).exists().hasFileName("letter.docx.pdf");
        assertThat(Files.readAllBytes(pdf)).isEqualTo(MINIMAL_PDF);
        assertThat(converter.attempts.get()).isEqualTo(1);
    }

    @Test
    void convert_retriesThenSucceeds() throws Exception {
        RecordingConverter converter = new RecordingConverter(props(2), officeManager, 2);
        Path pdf = converter.convert(sourceDocx);
        assertThat(pdf).exists();
        assertThat(converter.attempts.get()).isEqualTo(3);
    }

    @Test
    void convert_exhaustsRetriesAndWrapsLastOfficeException() {
        RecordingConverter converter = new RecordingConverter(props(1), officeManager, Integer.MAX_VALUE);
        assertThatThrownBy(() -> converter.convert(sourceDocx))
                .isInstanceOf(PdfConversionException.class)
                .hasMessageContaining("2 tentative")
                .hasCauseInstanceOf(OfficeException.class);
        assertThat(converter.attempts.get()).isEqualTo(2);
        assertThat(tempDir.resolve("letter.docx.pdf")).doesNotExist();
    }

    @Test
    void convert_treatsMissingOutputAsConversionFailureWithoutRetry() throws Exception {
        JodConverterPdfConverter converter = new JodConverterPdfConverter(props(2), officeManager) {
            @Override
            void executeConversion(Path sourceDocument, Path output) {
                // Pretend JOD succeeded but wrote nothing
            }
        };
        assertThatThrownBy(() -> converter.convert(sourceDocx))
                .isInstanceOf(PdfConversionException.class)
                .hasMessageContaining("sans fichier de sortie");
    }

    @Test
    void convert_usesAtLeastOneAttemptWhenMaxRetriesNegative() throws Exception {
        RecordingConverter converter = new RecordingConverter(
                new PdfProperties(true, 60, "127.0.0.1", 2002, -5),
                officeManager,
                0
        );
        converter.convert(sourceDocx);
        assertThat(converter.attempts.get()).isEqualTo(1);
    }

    @Test
    void destroy_stopsRunningManager() throws Exception {
        when(officeManager.isRunning()).thenReturn(true);
        JodConverterPdfConverter converter = newConverter(0);
        converter.destroy();
        verify(officeManager).stop();
    }

    @Test
    void destroy_skipsStopWhenNotRunning() throws Exception {
        when(officeManager.isRunning()).thenReturn(false);
        JodConverterPdfConverter converter = newConverter(0);
        converter.destroy();
        verify(officeManager, never()).stop();
    }

    @Test
    void destroy_swallowsStopFailures() throws Exception {
        when(officeManager.isRunning()).thenReturn(true);
        doThrow(new OfficeException("stop failed")).when(officeManager).stop();
        JodConverterPdfConverter converter = newConverter(0);
        converter.destroy();
        verify(officeManager).stop();
    }

    @Test
    void executeConversion_invokesLocalConverterAgainstOfficeManager() throws Exception {
        doThrow(new OfficeException("no libreoffice")).when(officeManager).execute(any());
        JodConverterPdfConverter converter = newConverter(0);
        Path output = tempDir.resolve("letter.docx.pdf");
        assertThatThrownBy(() -> converter.executeConversion(sourceDocx, output))
                .isInstanceOf(OfficeException.class)
                .hasMessageContaining("no libreoffice");
        verify(officeManager).execute(any());
    }

    @Test
    void convert_continuesWhenStaleOutputCannotBeDeleted() throws Exception {
        Path outputDir = tempDir.resolve("letter.docx.pdf");
        Files.createDirectory(outputDir);
        Files.writeString(outputDir.resolve("locked-child.txt"), "keep");

        JodConverterPdfConverter converter = new JodConverterPdfConverter(props(0), officeManager) {
            @Override
            void executeConversion(Path sourceDocument, Path output) throws OfficeException {
                throw new OfficeException("simulated LO failure");
            }
        };

        assertThatThrownBy(() -> converter.convert(sourceDocx))
                .isInstanceOf(PdfConversionException.class)
                .hasMessageContaining("1 tentative");
        assertThat(outputDir).exists();
    }

    @Test
    void publicConstructor_startsExternalManagerWithConnectFailFastDisabled() throws Exception {
        // connectFailFast=false → start() must not require a live LibreOffice listener.
        PdfProperties properties = new PdfProperties(true, 1, "127.0.0.1", 35999, 0);
        JodConverterPdfConverter converter = new JodConverterPdfConverter(properties);
        try {
            assertThat(converter).isNotNull();
        } finally {
            converter.destroy();
        }
    }

    private JodConverterPdfConverter newConverter(int maxRetries) {
        return new JodConverterPdfConverter(props(maxRetries), officeManager);
    }

    private static PdfProperties props(int maxRetries) {
        return new PdfProperties(true, 30, "127.0.0.1", 2002, maxRetries);
    }

    /** Test double that fails the first {@code failCount} attempts, then writes a PDF. */
    private static final class RecordingConverter extends JodConverterPdfConverter {
        private final AtomicInteger attempts = new AtomicInteger();
        private final int failCount;

        private RecordingConverter(PdfProperties properties, OfficeManager officeManager, int failCount) {
            super(properties, officeManager);
            this.failCount = failCount;
        }

        @Override
        void executeConversion(Path sourceDocument, Path output) throws OfficeException {
            int n = attempts.incrementAndGet();
            if (n <= failCount) {
                throw new OfficeException("simulated LO failure #" + n);
            }
            try {
                Files.write(output, MINIMAL_PDF);
            } catch (Exception ex) {
                throw new OfficeException("write failed", ex);
            }
        }
    }
}
