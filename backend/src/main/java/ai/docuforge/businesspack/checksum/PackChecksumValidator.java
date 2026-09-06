package ai.docuforge.businesspack.checksum;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SHA-256 checksum verification for DBPF-1 pack files (PRD §§41–42).
 * Does not resolve ZIP entries — caller supplies bytes/streams (pipeline Phase 8).
 */
@Component
public class PackChecksumValidator {

    private static final Pattern DECLARED_SHA256 = Pattern.compile("^sha256:[a-fA-F0-9]{64}$");
    private static final Pattern RAW_HEX = Pattern.compile("^[a-fA-F0-9]{64}$");

    public String digestPrefixed(byte[] content) {
        return "sha256:" + digestHex(content);
    }

    public String digestHex(byte[] content) {
        if (content == null) {
            content = new byte[0];
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public String digestHex(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("content stream required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dig = new DigestInputStream(in, digest)) {
                dig.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * Normalize declared checksum to lowercase 64-hex (without prefix).
     * Accepts {@code sha256:<hex>} (DBPF) or raw hex (DB storage adaptation).
     */
    public String normalizeDeclared(String declared) {
        if (!StringUtils.hasText(declared)) {
            return null;
        }
        String trimmed = declared.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("sha256:")) {
            String hex = lower.substring("sha256:".length());
            return RAW_HEX.matcher(hex).matches() ? hex : null;
        }
        if (RAW_HEX.matcher(trimmed).matches()) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    public boolean matches(String declared, byte[] content) {
        String expected = normalizeDeclared(declared);
        if (expected == null) {
            return false;
        }
        return expected.equals(digestHex(content));
    }

    public PackValidationIssue validateFile(String logicalPath, byte[] content, String declaredChecksum) {
        if (!StringUtils.hasText(declaredChecksum)) {
            return PackValidationIssue.warning(
                    "PACK_MANIFEST_CHECKSUM_UNDECLARED",
                    "error.pack.checksum_undeclared",
                    logicalPath
            );
        }
        if (normalizeDeclared(declaredChecksum) == null) {
            return PackValidationIssue.error("PACK_CHECKSUM_MISMATCH", "error.pack.checksum_mismatch")
                    .withFile(logicalPath);
        }
        if (content == null || !matches(declaredChecksum, content)) {
            return PackValidationIssue.error("PACK_CHECKSUM_MISMATCH", "error.pack.checksum_mismatch")
                    .withFile(logicalPath);
        }
        return null;
    }

    /**
     * Verifies every path present in {@code contentsByPath} that has a checksum entry.
     * Paths with checksum but no content are ignored here ({@code PACK_FILE_MISSING} is Phase 8).
     */
    public List<PackValidationIssue> validateContents(
            Map<String, String> checksums,
            Map<String, byte[]> contentsByPath
    ) {
        List<PackValidationIssue> issues = new ArrayList<>();
        if (contentsByPath == null || contentsByPath.isEmpty()) {
            return issues;
        }
        for (Map.Entry<String, byte[]> entry : contentsByPath.entrySet()) {
            String path = entry.getKey();
            String declared = checksums == null ? null : checksums.get(path);
            PackValidationIssue issue = validateFile(path, entry.getValue(), declared);
            if (issue != null) {
                issues.add(issue);
            }
        }
        return List.copyOf(issues);
    }

    public boolean isValidDeclaredFormat(String declared) {
        return StringUtils.hasText(declared) && DECLARED_SHA256.matcher(declared.trim()).matches();
    }
}
