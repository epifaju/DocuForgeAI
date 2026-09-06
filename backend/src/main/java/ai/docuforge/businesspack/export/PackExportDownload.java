package ai.docuforge.businesspack.export;

import org.springframework.core.io.ByteArrayResource;

/**
 * Exported DBPF-1 ZIP payload (PRD §§97–98).
 */
public record PackExportDownload(String filename, String contentType, ByteArrayResource body, long contentLength) {
}
