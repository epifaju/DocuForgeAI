package ai.docuforge.businesspack.archive;

import java.util.List;

/**
 * Result of a non-extracting ZIP security scan (Phase 4).
 */
public record PackArchiveInspection(
        long archiveBytes,
        int entryCount,
        long totalCompressedBytes,
        long totalUncompressedBytes,
        double compressionRatio,
        int templateDocxCount,
        List<String> entryNames
) {
}
