package ai.docuforge.domain.businesspack;

/**
 * Logical file role inside a DBPF-1 pack (stored on {@code business_pack_files.file_type}).
 */
public enum PackFileType {
    MANIFEST,
    TEMPLATE,
    METADATA,
    PROMPT,
    SAMPLE,
    PREVIEW,
    ASSET,
    ARCHIVE,
    OTHER
}
