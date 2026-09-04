package ai.docuforge.security.antivirus;

import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "docuforge.antivirus", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAntivirusScanner implements AntivirusScanner {

    @Override
    public void scan(Path file, String originalFilename) {
        // Antivirus disabled (default DEV / solo install).
    }
}
