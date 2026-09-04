package ai.docuforge.document.pdf;

import java.nio.file.Path;

/**
 * PDF conversion boundary (PRD §10).
 */
public interface PdfConverter {

    /**
     * Converts a source office document to PDF.
     *
     * @param sourceDocument path to the input file (typically DOCX)
     * @return path to the generated PDF (caller owns cleanup of both files)
     */
    Path convert(Path sourceDocument);
}