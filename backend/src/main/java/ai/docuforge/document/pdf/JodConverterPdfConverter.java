package ai.docuforge.document.pdf;

import ai.docuforge.config.PdfProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.ExternalOfficeManager;
import org.jodconverter.local.task.LoadDocumentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * JODConverter implementation connecting to an external LibreOffice UNO listener (PRD §10).
 * Uses {@link LoadDocumentMode#REMOTE} so Docker LO need not share the host filesystem.
 */
@Component
@ConditionalOnProperty(prefix = "docuforge.pdf", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(PdfConverter.class)
public class JodConverterPdfConverter implements PdfConverter, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JodConverterPdfConverter.class);

    private final PdfProperties properties;
    private final OfficeManager officeManager;

    public JodConverterPdfConverter(PdfProperties properties) throws OfficeException {
        this(properties, createAndStartManager(properties));
        log.info(
                "JODConverter connected to LibreOffice UNO at {}:{}",
                properties.libreofficeHost(),
                properties.libreofficePort()
        );
    }

    /** Package-visible for unit tests (inject a mock / fake {@link OfficeManager}). */
    JodConverterPdfConverter(PdfProperties properties, OfficeManager officeManager) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.officeManager = Objects.requireNonNull(officeManager, "officeManager");
    }

    private static OfficeManager createAndStartManager(PdfProperties properties) throws OfficeException {
        OfficeManager manager = ExternalOfficeManager.builder()
                .hostName(properties.libreofficeHost())
                .portNumbers(properties.libreofficePort())
                .connectOnStart(true)
                .connectFailFast(false)
                .taskExecutionTimeout(Math.max(1, properties.timeoutSeconds()) * 1000L)
                .build();
        manager.start();
        return manager;
    }

    @Override
    public Path convert(Path sourceDocument) {
        Objects.requireNonNull(sourceDocument, "sourceDocument");
        if (!Files.isRegularFile(sourceDocument)) {
            throw new PdfConversionException("Fichier source DOCX introuvable pour conversion PDF.");
        }

        Path output = sourceDocument.resolveSibling(sourceDocument.getFileName().toString() + ".pdf");
        int attempts = Math.max(1, properties.maxRetries() + 1);
        OfficeException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                executeConversion(sourceDocument, output);
                if (!Files.isRegularFile(output)) {
                    throw new PdfConversionException("Conversion PDF terminee sans fichier de sortie.");
                }
                return output;
            } catch (OfficeException ex) {
                last = ex;
                log.warn("Tentative PDF {}/{} echouee: {}", i, attempts, ex.getMessage());
                try {
                    Files.deleteIfExists(output);
                } catch (Exception ignored) {
                    // best effort
                }
            }
        }
        throw new PdfConversionException(
                "Conversion PDF impossible apres " + attempts + " tentative(s).",
                last
        );
    }

    /**
     * Runs the JOD conversion. Overridable in tests to avoid a live LibreOffice process.
     */
    void executeConversion(Path sourceDocument, Path output) throws OfficeException {
        LocalConverter.builder()
                .officeManager(officeManager)
                .loadDocumentMode(LoadDocumentMode.REMOTE)
                .build()
                .convert(sourceDocument.toFile())
                .to(output.toFile())
                .execute();
    }

    @Override
    public void destroy() {
        try {
            if (officeManager != null && officeManager.isRunning()) {
                officeManager.stop();
            }
        } catch (OfficeException ex) {
            log.warn("Arret OfficeManager: {}", ex.getMessage());
        }
    }
}
