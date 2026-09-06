package ai.docuforge.businesspack.importjob;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cleans expired pack import staging (PRD §133 / PackProperties.importRetentionHours).
 */
@Component
public class PackImportRetentionScheduler {

    private final PackImportService packImportService;

    public PackImportRetentionScheduler(PackImportService packImportService) {
        this.packImportService = packImportService;
    }

    @Scheduled(fixedDelayString = "${docuforge.packs.import-retention-sweep-ms:3600000}")
    public void sweep() {
        packImportService.expireStaleImports();
    }
}
