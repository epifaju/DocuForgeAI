package ai.docuforge.businesspack.checksum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.manifest.PackValidationSeverity;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackChecksumValidatorTest {

    private PackChecksumValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PackChecksumValidator();
    }

    @Test
    void digestsMatchPrefixedFormat() {
        byte[] content = "hello-pack".getBytes(StandardCharsets.UTF_8);
        String prefixed = validator.digestPrefixed(content);
        assertThat(prefixed).startsWith("sha256:");
        assertThat(validator.isValidDeclaredFormat(prefixed)).isTrue();
        assertThat(validator.matches(prefixed, content)).isTrue();
    }

    @Test
    void acceptsRawHexFromStorageAdaptation() {
        byte[] content = "raw-hex".getBytes(StandardCharsets.UTF_8);
        String hex = validator.digestHex(content);
        assertThat(validator.matches(hex, content)).isTrue();
        assertThat(validator.normalizeDeclared(hex)).isEqualTo(hex.toLowerCase());
    }

    @Test
    void mismatchProducesErrorWithFile() {
        byte[] content = "actual".getBytes(StandardCharsets.UTF_8);
        String wrong = validator.digestPrefixed("other".getBytes(StandardCharsets.UTF_8));
        PackValidationIssue issue = validator.validateFile("templates/a.docx", content, wrong);
        assertThat(issue).isNotNull();
        assertThat(issue.severity()).isEqualTo(PackValidationSeverity.ERROR);
        assertThat(issue.code()).isEqualTo("PACK_CHECKSUM_MISMATCH");
        assertThat(issue.file()).isEqualTo("templates/a.docx");
    }

    @Test
    void undeclaredChecksumIsWarning() {
        PackValidationIssue issue = validator.validateFile(
                "templates/a.docx",
                "x".getBytes(StandardCharsets.UTF_8),
                null
        );
        assertThat(issue.severity()).isEqualTo(PackValidationSeverity.WARNING);
        assertThat(issue.code()).isEqualTo("PACK_MANIFEST_CHECKSUM_UNDECLARED");
    }

    @Test
    void validateContentsReportsOnlyProvidedPaths() {
        byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
        byte[] bad = "bad".getBytes(StandardCharsets.UTF_8);
        Map<String, String> checksums = Map.of(
                "templates/ok.docx", validator.digestPrefixed(ok),
                "templates/missing.docx", validator.digestPrefixed(ok)
        );
        Map<String, byte[]> contents = Map.of(
                "templates/ok.docx", ok,
                "templates/bad.docx", bad
        );

        var issues = validator.validateContents(checksums, contents);
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().code()).isEqualTo("PACK_MANIFEST_CHECKSUM_UNDECLARED");
        assertThat(issues.getFirst().file()).isEqualTo("templates/bad.docx");
    }

    @Test
    void streamDigestMatchesByteDigest() throws Exception {
        byte[] content = "streamed".getBytes(StandardCharsets.UTF_8);
        assertThat(validator.digestHex(new ByteArrayInputStream(content)))
                .isEqualTo(validator.digestHex(content));
    }

    @Test
    void nullAndMalformedDeclaredChecksums() throws Exception {
        assertThat(validator.digestHex((byte[]) null)).hasSize(64);
        assertThatThrownBy(() -> validator.digestHex((java.io.InputStream) null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(validator.normalizeDeclared(null)).isNull();
        assertThat(validator.normalizeDeclared("  ")).isNull();
        assertThat(validator.normalizeDeclared("sha256:zzzz")).isNull();
        assertThat(validator.normalizeDeclared("sha256:abcd")).isNull();
        assertThat(validator.isValidDeclaredFormat("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .isFalse();
        assertThat(validator.isValidDeclaredFormat("sha256:short")).isFalse();

        PackValidationIssue malformed = validator.validateFile(
                "templates/a.docx",
                "x".getBytes(StandardCharsets.UTF_8),
                "sha256:not-hex"
        );
        assertThat(malformed.code()).isEqualTo("PACK_CHECKSUM_MISMATCH");

        PackValidationIssue nullContent = validator.validateFile(
                "templates/a.docx",
                null,
                validator.digestPrefixed("x".getBytes(StandardCharsets.UTF_8))
        );
        assertThat(nullContent.code()).isEqualTo("PACK_CHECKSUM_MISMATCH");

        assertThat(validator.validateContents(Map.of("a", "sha256:aa"), null)).isEmpty();
        assertThat(validator.validateContents(null, Map.of("a.docx", "x".getBytes(StandardCharsets.UTF_8))))
                .anyMatch(i -> "PACK_MANIFEST_CHECKSUM_UNDECLARED".equals(i.code()));
    }
}
