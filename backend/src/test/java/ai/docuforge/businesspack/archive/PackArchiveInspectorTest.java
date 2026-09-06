package ai.docuforge.businesspack.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.config.PackProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackArchiveInspectorTest {

    @TempDir
    Path tempDir;

    private PackArchiveInspector inspector;
    private PackProperties tightLimits;

    @BeforeEach
    void setUp() {
        tightLimits = new PackProperties(
                true,
                1, // 1 MB upload
                2, // 2 MB uncompressed
                20,
                5,
                1, // 1 MB single file
                10,
                24,
                "0.1.0",
                Set.of("json", "docx", "txt", "md", "csv", "png", "jpg", "jpeg", "webp")
        );
        inspector = new PackArchiveInspector(tightLimits);
    }

    @Test
    void acceptsValidMinimalPackArchive() throws Exception {
        Path zip = tempDir.resolve("valid.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "manifest.json", "{\"schemaVersion\":\"DBPF-1\"}");
            putText(out, "templates/", "");
            putBytes(out, "templates/demo.docx", new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00});
            putText(out, "metadata/demo.json", "{}");
        }

        PackArchiveInspection result = inspector.inspect(zip);
        assertThat(result.entryCount()).isGreaterThanOrEqualTo(3);
        assertThat(result.templateDocxCount()).isEqualTo(1);
        assertThat(result.entryNames()).anyMatch(n -> n.endsWith("manifest.json"));
    }

    @Test
    void rejectsCorruptedZip() throws Exception {
        Path zip = tempDir.resolve("corrupt.zip");
        Files.writeString(zip, "not-a-zip");

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_ARCHIVE_INVALID");
    }

    @Test
    void rejectsEmptyZip() throws Exception {
        Path zip = tempDir.resolve("empty.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            // no entries
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_ARCHIVE_INVALID");
    }

    @Test
    void rejectsNestedZipAsUnsupportedExtension() throws Exception {
        Path zip = tempDir.resolve("nested.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "manifest.json", "{}");
            putBytes(out, "assets/inner.zip", new byte[] {1, 2, 3});
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void rejectsZipSlipTraversal() throws Exception {
        Path zip = tempDir.resolve("slip.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "templates/../../evil.txt", "pwned");
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSAFE_PATH");
    }

    @Test
    void rejectsAbsoluteUnixPath() throws Exception {
        Path zip = tempDir.resolve("abs.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "/etc/passwd", "root");
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSAFE_PATH");
    }

    @Test
    void rejectsWindowsAbsolutePath() throws Exception {
        Path zip = tempDir.resolve("win.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "C:/Windows/system32/evil.txt", "x");
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSAFE_PATH");
    }

    @Test
    void rejectsBackslashTraversal() throws Exception {
        Path zip = tempDir.resolve("win-slip.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "..\\..\\evil.txt", "x");
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSAFE_PATH");
    }

    @Test
    void rejectsSymlinkEntry() throws Exception {
        Path zip = tempDir.resolve("symlink.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "manifest.json", "{}");
            ZipArchiveEntry link = new ZipArchiveEntry("link-to-secret");
            link.setUnixMode(UnixStat.LINK_FLAG | 0644);
            byte[] target = "/tmp/secret".getBytes(StandardCharsets.UTF_8);
            link.setSize(target.length);
            link.setMethod(ZipArchiveEntry.STORED);
            CRC32 crc = new CRC32();
            crc.update(target);
            link.setCrc(crc.getValue());
            out.putArchiveEntry(link);
            out.write(target);
            out.closeArchiveEntry();
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSAFE_PATH");
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        Path zip = tempDir.resolve("exe.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "manifest.json", "{}");
            putBytes(out, "tools/run.exe", new byte[] {0x4D, 0x5A});
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void rejectsDocm() throws Exception {
        Path zip = tempDir.resolve("docm.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putBytes(out, "templates/macro.docm", new byte[] {1});
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void rejectsDuplicateEntryNames() throws Exception {
        Path zip = tempDir.resolve("dup.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "manifest.json", "one");
            putText(out, "manifest.json", "two");
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_ARCHIVE_INVALID");
    }

    @Test
    void rejectsNormalizedPathDuplicates() throws Exception {
        Path zip = tempDir.resolve("dup-norm.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "./manifest.json", "one");
            putText(out, "manifest.json", "two");
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_ARCHIVE_INVALID");
    }

    @Test
    void rejectsNullByteInEntryName() throws Exception {
        Path zip = tempDir.resolve("nullbyte.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "evil\0.txt", "x");
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_UNSAFE_PATH");
    }

    @Test
    void rejectsOversizedSingleEntry() throws Exception {
        Path zip = tempDir.resolve("huge-entry.zip");
        byte[] chunk = new byte[1_100_000];
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            out.setLevel(Deflater.BEST_COMPRESSION);
            putBytes(out, "samples/big.txt", chunk);
        }

        assertThatThrownBy(() -> inspector.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_ARCHIVE_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsTooManyEntries() throws Exception {
        PackArchiveInspector limited = new PackArchiveInspector(new PackProperties(
                true, 10, 10, 3, 5, 5, 50, 24, "0.1.0", Set.of("txt", "json")
        ));
        Path zip = tempDir.resolve("many.zip");
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            putText(out, "a.txt", "1");
            putText(out, "b.txt", "2");
            putText(out, "c.txt", "3");
            putText(out, "d.txt", "4");
        }

        assertThatThrownBy(() -> limited.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_ARCHIVE_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsCompressionBombRatio() throws Exception {
        PackArchiveInspector limited = new PackArchiveInspector(new PackProperties(
                true, 10, 50, 50, 20, 50, 2, 24, "0.1.0", Set.of("txt", "json")
        ));
        Path zip = tempDir.resolve("bomb.zip");
        byte[] zeros = new byte[200_000];
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            out.setLevel(Deflater.BEST_COMPRESSION);
            putBytes(out, "manifest.json", "{}".getBytes(StandardCharsets.UTF_8));
            putBytes(out, "samples/zeros.txt", zeros);
        }

        assertThatThrownBy(() -> limited.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_ARCHIVE_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsOversizedArchiveFile() throws Exception {
        PackArchiveInspector tiny = new PackArchiveInspector(new PackProperties(
                true, 0, 10, 50, 20, 5, 50, 24, "0.1.0", Set.of("txt")
        ));
        // maxUploadSizeMb <= 0 becomes 100 via defaults — force via inspecting large file differently:
        // Use a custom properties by constructing after defaults: maxUpload 100MB is huge.
        // Instead write inspector with reflection-free approach: use 1 byte limit by creating
        // PackProperties and relying on compact ctor only when <=0. So pass maxUploadSizeMb=1
        // and create zip larger than 1MB.
        PackArchiveInspector oneMb = new PackArchiveInspector(new PackProperties(
                true, 1, 10, 50, 20, 5, 50, 24, "0.1.0", Set.of("txt", "bin")
        ));
        // allowed doesn't include bin — use txt with huge content
        Path zip = tempDir.resolve("huge.zip");
        byte[] chunk = new byte[300_000];
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            out.setLevel(Deflater.NO_COMPRESSION);
            for (int i = 0; i < 5; i++) {
                putBytes(out, "part-" + i + ".txt", chunk);
            }
        }

        assertThat(Files.size(zip)).isGreaterThan(1L * 1024 * 1024);
        assertThatThrownBy(() -> oneMb.inspect(zip))
                .isInstanceOf(PackArchiveException.class)
                .extracting(ex -> ((PackArchiveException) ex).getCode())
                .isEqualTo("PACK_FILE_TOO_LARGE");
    }

    private static void putText(ZipArchiveOutputStream out, String name, String content) throws IOException {
        if (name.endsWith("/")) {
            ZipArchiveEntry dir = new ZipArchiveEntry(name);
            out.putArchiveEntry(dir);
            out.closeArchiveEntry();
            return;
        }
        putBytes(out, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(ZipArchiveOutputStream out, String name, byte[] data) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        out.putArchiveEntry(entry);
        out.write(data);
        out.closeArchiveEntry();
    }
}
