package ai.docuforge.storage;

import java.nio.file.Files;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class StorageHealthIndicator implements HealthIndicator {

    private final LocalStorageProvider storageProvider;

    public StorageHealthIndicator(LocalStorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    @Override
    public Health health() {
        var root = storageProvider.rootPath();
        if (Files.isDirectory(root) && Files.isWritable(root)) {
            return Health.up().withDetail("root", root.toString()).build();
        }
        return Health.down().withDetail("root", root.toString()).build();
    }
}