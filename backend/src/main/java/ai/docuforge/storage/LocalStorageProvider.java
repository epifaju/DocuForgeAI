package ai.docuforge.storage;

import ai.docuforge.config.StorageProperties;
import ai.docuforge.security.antivirus.AntivirusScanner;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);

    private final StorageProperties properties;
    private final AntivirusScanner antivirusScanner;
    private final Path root;
    private final Set<String> allowedExtensions;
    private final Set<String> allowedContentTypes;
    private final long maxBytes;

    public LocalStorageProvider(StorageProperties properties, AntivirusScanner antivirusScanner) {
        this.properties = properties;
        this.antivirusScanner = antivirusScanner;
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
        Set<String> extensions = properties.allowedExtensions() == null
                ? Set.of()
                : properties.allowedExtensions();
        Set<String> contentTypes = properties.allowedContentTypes() == null
                ? Set.of()
                : properties.allowedContentTypes();
        this.allowedExtensions = extensions.stream()
                .map(ext -> ext.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.allowedContentTypes = contentTypes.stream()
                .map(type -> type.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.maxBytes = Math.max(properties.maxUploadSizeMb(), 1) * 1024L * 1024L;
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
            for (StorageCategory category : StorageCategory.values()) {
                Files.createDirectories(root.resolve(category.directory()));
            }
            if (!Files.isWritable(root)) {
                throw new StorageException(
                        "STORAGE_NOT_WRITABLE",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Le rÃ©pertoire de stockage n'est pas accessible en Ã©criture."
                );
            }
            log.info("Local storage ready at {}", root);
        } catch (IOException ex) {
            throw new StorageException(
                    "STORAGE_INIT_FAILED",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Impossible d'initialiser le stockage local.",
                    ex
            );
        }
    }

    @Override
    public StoredFile store(
            StorageCategory category,
            String originalFilename,
            String contentType,
            InputStream content,
            long contentLength
    ) {
        if (category == null) {
            throw new StorageException("INVALID_CATEGORY", HttpStatus.BAD_REQUEST, "CatÃ©gorie de stockage manquante.");
        }
        if (content == null) {
            throw new StorageException("INVALID_CONTENT", HttpStatus.BAD_REQUEST, "Contenu fichier manquant.");
        }
        if (contentLength < 0) {
            throw new StorageException("INVALID_SIZE", HttpStatus.BAD_REQUEST, "Taille de fichier invalide.");
        }
        if (contentLength > maxBytes) {
            throw new StorageException(
                    "PAYLOAD_TOO_LARGE",
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Fichier trop volumineux (max " + properties.maxUploadSizeMb() + " Mo)."
            );
        }

        String safeOriginal = StoragePathGuard.sanitizeOriginalFilename(originalFilename);
        String extension = StoragePathGuard.extractExtension(safeOriginal);
        validateExtension(extension);
        validateContentType(contentType);

        String storageKey = category.directory() + "/" + UUID.randomUUID() + "." + extension;
        Path target = StoragePathGuard.resolveUnderRoot(root, storageKey);
        Path temp = root.resolve(StorageCategory.TEMPORARY.directory())
                .resolve(UUID.randomUUID() + ".upload");

        try {
            Files.createDirectories(target.getParent());
            long written;
            try (InputStream in = content; OutputStream out = Files.newOutputStream(temp)) {
                written = in.transferTo(out);
            }
            if (contentLength > 0 && written != contentLength) {
                Files.deleteIfExists(temp);
                throw new StorageException(
                        "SIZE_MISMATCH",
                        HttpStatus.BAD_REQUEST,
                        "Taille du flux incohÃ©rente avec la taille dÃ©clarÃ©e."
                );
            }
            if (written > maxBytes) {
                Files.deleteIfExists(temp);
                throw new StorageException(
                        "PAYLOAD_TOO_LARGE",
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Fichier trop volumineux (max " + properties.maxUploadSizeMb() + " Mo)."
                );
            }
            antivirusScanner.scan(temp, safeOriginal);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(
                    storageKey,
                    safeOriginal,
                    contentType == null ? null : contentType.toLowerCase(Locale.ROOT),
                    extension,
                    written,
                    category
            );
        } catch (StorageException ex) {
            throw ex;
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // best effort cleanup
            }
            throw new StorageException(
                    "STORAGE_WRITE_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ã‰chec d'Ã©criture du fichier.",
                    ex
            );
        }
    }

    @Override
    public InputStream read(String storageKey) {
        Path path = StoragePathGuard.resolveUnderRoot(root, storageKey);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new StorageException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND, "Fichier introuvable.");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException ex) {
            throw new StorageException(
                    "STORAGE_READ_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ã‰chec de lecture du fichier.",
                    ex
            );
        }
    }

    @Override
    public void delete(String storageKey) {
        Path path = StoragePathGuard.resolveUnderRoot(root, storageKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new StorageException(
                    "STORAGE_DELETE_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ã‰chec de suppression du fichier.",
                    ex
            );
        }
    }

    @Override
    public boolean exists(String storageKey) {
        Path path = StoragePathGuard.resolveUnderRoot(root, storageKey);
        return Files.isRegularFile(path);
    }

    Path rootPath() {
        return root;
    }

    private void validateExtension(String extension) {
        if (!allowedExtensions.contains(extension)) {
            throw new StorageException(
                    "INVALID_EXTENSION",
                    HttpStatus.BAD_REQUEST,
                    "Extension non autorisÃ©e: ." + extension
            );
        }
    }

    private void validateContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new StorageException("INVALID_MIME", HttpStatus.BAD_REQUEST, "Type MIME manquant.");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (!allowedContentTypes.contains(normalized)) {
            throw new StorageException(
                    "INVALID_MIME",
                    HttpStatus.BAD_REQUEST,
                    "Type MIME non autorisÃ©: " + normalized
            );
        }
    }
}