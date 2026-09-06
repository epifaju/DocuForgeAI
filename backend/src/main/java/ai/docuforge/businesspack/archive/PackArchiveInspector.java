package ai.docuforge.businesspack.archive;

import ai.docuforge.config.PackProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Pass-1 DBPF-1 ZIP security inspector: headers only, no extraction (PRD §§17–21, §63).
 */
@Component
public class PackArchiveInspector {

    private final PackProperties properties;

    public PackArchiveInspector(PackProperties properties) {
        this.properties = properties;
    }

    public PackArchiveInspection inspect(Path archivePath) {
        if (archivePath == null) {
            throw invalid("error.pack.archive_invalid");
        }
        if (!Files.isRegularFile(archivePath)) {
            throw invalid("error.pack.archive_invalid");
        }

        long archiveBytes;
        try {
            archiveBytes = Files.size(archivePath);
        } catch (IOException ex) {
            throw invalid("error.pack.archive_invalid", ex);
        }

        if (archiveBytes <= 0) {
            throw invalid("error.pack.archive_invalid");
        }
        if (archiveBytes > properties.maxUploadBytes()) {
            throw tooLarge("error.pack.file_too_large");
        }

        try (ZipFile zipFile = ZipFile.builder().setPath(archivePath).get()) {
            return scanEntries(archiveBytes, zipFile);
        } catch (PackArchiveException ex) {
            throw ex;
        } catch (IOException ex) {
            throw invalid("error.pack.archive_invalid", ex);
        }
    }

    private PackArchiveInspection scanEntries(long archiveBytes, ZipFile zipFile) {
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        Set<String> seenNames = new HashSet<>();
        List<String> names = new ArrayList<>();

        long totalCompressed = 0L;
        long totalUncompressed = 0L;
        int entryCount = 0;
        int templateDocxCount = 0;

        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            entryCount++;
            if (entryCount > properties.maxFiles()) {
                throw limitExceeded("error.pack.archive_limit_exceeded");
            }

            String rawName = entry.getName();
            assertSafeEntryName(rawName);
            String canonicalName = normalizeEntryKey(rawName);

            if (!seenNames.add(canonicalName)) {
                throw invalid("error.pack.archive_duplicate_entry");
            }
            names.add(canonicalName);

            if (entry.isUnixSymlink()) {
                throw unsafePath("error.pack.unsafe_path");
            }

            boolean directory = entry.isDirectory() || canonicalName.endsWith("/");
            if (!directory) {
                assertAllowedExtension(canonicalName);
            }

            long compressed = entry.getCompressedSize();
            long uncompressed = entry.getSize();
            if (compressed < 0 || uncompressed < 0) {
                // Central directory must expose sizes for bomb protection (PRD §19).
                throw invalid("error.pack.archive_invalid");
            }

            if (!directory && uncompressed > properties.maxSingleFileBytes()) {
                throw limitExceeded("error.pack.archive_limit_exceeded");
            }

            totalCompressed += compressed;
            totalUncompressed += uncompressed;

            if (totalUncompressed > properties.maxUncompressedBytes()) {
                throw limitExceeded("error.pack.archive_limit_exceeded");
            }

            if (isTemplateDocx(canonicalName)) {
                templateDocxCount++;
                if (templateDocxCount > properties.maxTemplates()) {
                    throw limitExceeded("error.pack.archive_limit_exceeded");
                }
            }
        }

        if (entryCount == 0) {
            throw invalid("error.pack.archive_invalid");
        }

        double ratio = compressionRatio(totalCompressed, totalUncompressed);
        if (totalCompressed > 0 && ratio > properties.maxCompressionRatio()) {
            throw limitExceeded("error.pack.archive_limit_exceeded");
        }

        return new PackArchiveInspection(
                archiveBytes,
                entryCount,
                totalCompressed,
                totalUncompressed,
                ratio,
                templateDocxCount,
                List.copyOf(names)
        );
    }

    static void assertSafeEntryName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0) {
            throw unsafePath("error.pack.unsafe_path");
        }
        String normalizedSlashes = normalizeEntryKey(name);
        if (normalizedSlashes.startsWith("/") || normalizedSlashes.startsWith("//")) {
            throw unsafePath("error.pack.unsafe_path");
        }
        if (normalizedSlashes.matches("^[A-Za-z]:/.*")) {
            throw unsafePath("error.pack.unsafe_path");
        }

        Path root = Path.of("pack-root").toAbsolutePath().normalize();
        Path resolved = root.resolve(normalizedSlashes).normalize();
        if (!resolved.startsWith(root)) {
            throw unsafePath("error.pack.unsafe_path");
        }

        for (String segment : normalizedSlashes.split("/")) {
            if ("..".equals(segment)) {
                throw unsafePath("error.pack.unsafe_path");
            }
        }
    }

    /**
     * Canonical entry key for duplicate detection and path safety (PRD §§141–142).
     * Collapses {@code \} → {@code /} and strips leading {@code ./} segments.
     */
    static String normalizeEntryKey(String name) {
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.contains("/./")) {
            normalized = normalized.replace("/./", "/");
        }
        return normalized;
    }

    private void assertAllowedExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace('\\', '/');
        int slash = lower.lastIndexOf('/');
        String fileName = slash >= 0 ? lower.substring(slash + 1) : lower;
        if (fileName.isBlank()) {
            throw unsupportedType("error.pack.unsupported_file_type");
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            throw unsupportedType("error.pack.unsupported_file_type");
        }
        String ext = fileName.substring(dot + 1);
        if ("docm".equals(ext) || !properties.allowedExtensions().contains(ext)) {
            throw unsupportedType("error.pack.unsupported_file_type");
        }
    }

    private static boolean isTemplateDocx(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (!lower.endsWith(".docx")) {
            return false;
        }
        return lower.startsWith("templates/") || lower.contains("/templates/");
    }

    private static double compressionRatio(long compressed, long uncompressed) {
        if (compressed <= 0) {
            return uncompressed > 0 ? Double.POSITIVE_INFINITY : 0.0d;
        }
        return (double) uncompressed / (double) compressed;
    }

    private static PackArchiveException invalid(String message) {
        return new PackArchiveException("PACK_ARCHIVE_INVALID", HttpStatus.BAD_REQUEST, message);
    }

    private static PackArchiveException invalid(String message, Throwable cause) {
        return new PackArchiveException("PACK_ARCHIVE_INVALID", HttpStatus.BAD_REQUEST, message, cause);
    }

    private static PackArchiveException limitExceeded(String message) {
        return new PackArchiveException("PACK_ARCHIVE_LIMIT_EXCEEDED", HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    private static PackArchiveException tooLarge(String message) {
        return new PackArchiveException("PACK_FILE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    private static PackArchiveException unsafePath(String message) {
        return new PackArchiveException("PACK_UNSAFE_PATH", HttpStatus.BAD_REQUEST, message);
    }

    private static PackArchiveException unsupportedType(String message) {
        return new PackArchiveException("PACK_UNSUPPORTED_FILE_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}
