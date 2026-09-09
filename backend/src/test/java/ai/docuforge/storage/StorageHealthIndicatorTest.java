package ai.docuforge.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class StorageHealthIndicatorTest {

    @TempDir
    Path tempDir;

    @Test
    void reportsUpWhenRootWritable() throws Exception {
        Path root = tempDir.resolve("storage");
        Files.createDirectories(root);
        LocalStorageProvider provider = mock(LocalStorageProvider.class);
        when(provider.rootPath()).thenReturn(root);

        Health health = new StorageHealthIndicator(provider).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("root", root.toString());
    }

    @Test
    void reportsDownWhenRootMissing() {
        Path missing = tempDir.resolve("missing-root");
        LocalStorageProvider provider = mock(LocalStorageProvider.class);
        when(provider.rootPath()).thenReturn(missing);

        Health health = new StorageHealthIndicator(provider).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
