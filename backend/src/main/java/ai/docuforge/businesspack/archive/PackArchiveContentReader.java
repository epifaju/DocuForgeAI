package ai.docuforge.businesspack.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Post-scan ZIP content access with DBPF-1 virtual-root normalization (PRD §16).
 * Reads entry bytes in memory; does not write to StorageProvider.
 */
@Component
public class PackArchiveContentReader {

    public PackArchiveContents open(ZipFile zipFile, List<String> entryNames) {
        String rootPrefix = resolveRootPrefix(entryNames)
                .orElseThrow(() -> new PackArchiveException(
                        "PACK_ARCHIVE_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "error.pack.archive_invalid"
                ));

        Map<String, String> logicalToRaw = new HashMap<>();
        for (String raw : entryNames) {
            String normalized = raw.replace('\\', '/');
            if (normalized.endsWith("/")) {
                continue;
            }
            if (!rootPrefix.isEmpty() && !normalized.startsWith(rootPrefix)) {
                continue;
            }
            String logical = rootPrefix.isEmpty() ? normalized : normalized.substring(rootPrefix.length());
            if (logical.isBlank()) {
                continue;
            }
            logicalToRaw.putIfAbsent(logical, raw);
        }
        return new PackArchiveContents(zipFile, rootPrefix, Map.copyOf(logicalToRaw));
    }

    /**
     * Accepts either pack-root entries ({@code manifest.json}) or a single top-level directory.
     */
    public Optional<String> resolveRootPrefix(List<String> entryNames) {
        if (entryNames == null || entryNames.isEmpty()) {
            return Optional.empty();
        }

        Set<String> files = new HashSet<>();
        for (String raw : entryNames) {
            String name = raw.replace('\\', '/');
            if (!name.endsWith("/")) {
                files.add(name);
            }
        }

        if (files.contains("manifest.json")) {
            return Optional.of("");
        }

        Set<String> candidateDirs = new HashSet<>();
        for (String file : files) {
            int slash = file.indexOf('/');
            if (slash > 0) {
                candidateDirs.add(file.substring(0, slash));
            }
        }

        List<String> withManifest = new ArrayList<>();
        for (String dir : candidateDirs) {
            if (files.contains(dir + "/manifest.json")) {
                withManifest.add(dir);
            }
        }
        if (withManifest.size() == 1) {
            return Optional.of(withManifest.getFirst() + "/");
        }
        return Optional.empty();
    }

    public record PackArchiveContents(
            ZipFile zipFile,
            String rootPrefix,
            Map<String, String> logicalToRaw
    ) {
        public boolean has(String logicalPath) {
            return logicalPath != null && logicalToRaw.containsKey(logicalPath);
        }

        public Set<String> logicalPaths() {
            return logicalToRaw.keySet();
        }

        public byte[] readBytes(String logicalPath) throws IOException {
            String raw = logicalToRaw.get(logicalPath);
            if (raw == null) {
                return null;
            }
            ZipArchiveEntry entry = zipFile.getEntry(raw);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zipFile.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }

        public String readUtf8(String logicalPath) throws IOException {
            byte[] bytes = readBytes(logicalPath);
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
