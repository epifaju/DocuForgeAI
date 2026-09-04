package ai.docuforge.storage;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public final class StoragePathGuard {

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-z]+/[0-9a-fA-F-]{36}\\.[a-z0-9]{1,10}$");

    private StoragePathGuard() {
    }

    public static String sanitizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new StorageException("INVALID_FILENAME", HttpStatus.BAD_REQUEST, "Nom de fichier manquant.");
        }
        String trimmed = originalFilename.trim();
        if (trimmed.length() > 255) {
            throw new StorageException("INVALID_FILENAME", HttpStatus.BAD_REQUEST, "Nom de fichier trop long.");
        }
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("\0")) {
            throw new StorageException("PATH_TRAVERSAL", HttpStatus.BAD_REQUEST, "Nom de fichier non autorisé.");
        }
        return trimmed;
    }

    public static String extractExtension(String originalFilename) {
        String name = sanitizeOriginalFilename(originalFilename);
        int idx = name.lastIndexOf('.');
        if (idx <= 0 || idx == name.length() - 1) {
            throw new StorageException("INVALID_EXTENSION", HttpStatus.BAD_REQUEST, "Extension de fichier manquante.");
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    static void assertSafeStorageKey(String storageKey) {
        if (!StringUtils.hasText(storageKey) || !SAFE_KEY.matcher(storageKey).matches()) {
            throw new StorageException("INVALID_STORAGE_KEY", HttpStatus.BAD_REQUEST, "Clé de stockage invalide.");
        }
        if (storageKey.contains("..")) {
            throw new StorageException("PATH_TRAVERSAL", HttpStatus.BAD_REQUEST, "Clé de stockage non autorisée.");
        }
    }

    static Path resolveUnderRoot(Path root, String storageKey) {
        assertSafeStorageKey(storageKey);
        Path rootNormalized = root.toAbsolutePath().normalize();
        Path resolved = rootNormalized.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootNormalized)) {
            throw new StorageException("PATH_TRAVERSAL", HttpStatus.BAD_REQUEST, "Chemin hors zone de stockage.");
        }
        return resolved;
    }
}