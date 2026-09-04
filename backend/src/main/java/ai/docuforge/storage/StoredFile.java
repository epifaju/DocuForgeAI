package ai.docuforge.storage;

public record StoredFile(
        String storageKey,
        String originalFilename,
        String contentType,
        String extension,
        long sizeBytes,
        StorageCategory category
) {
}