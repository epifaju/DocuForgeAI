package ai.docuforge.storage;

import java.io.InputStream;

/**
 * Abstraction over physical file storage (PRD §33).
 * Business code must never build filesystem paths directly.
 */
public interface StorageProvider {

    StoredFile store(
            StorageCategory category,
            String originalFilename,
            String contentType,
            InputStream content,
            long contentLength
    );

    InputStream read(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);
}