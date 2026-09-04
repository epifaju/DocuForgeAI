package ai.docuforge.security.antivirus;

import java.nio.file.Path;

/**
 * Optional malware scan before persisting uploads (PRD §34).
 */
public interface AntivirusScanner {

    void scan(Path file, String originalFilename);
}
