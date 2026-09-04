package ai.docuforge.storage;

public enum StorageCategory {
    TEMPLATES("templates"),
    GENERATED("generated"),
    TEMPORARY("temporary");

    private final String directory;

    StorageCategory(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}