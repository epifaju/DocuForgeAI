package ai.docuforge.businesspack.template;

import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.manifest.PackValidationSeverity;
import ai.docuforge.template.parser.DetectedVariable;
import ai.docuforge.template.parser.DocxVariableParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * DOCX structure + placeholder extraction + metadata coherence (PRD §§33–35, §§138–140).
 * Reuses {@link DocxVariableParser}; does not install or touch StorageProvider.
 */
@Component
public class PackTemplateValidator {

    private final DocxVariableParser docxVariableParser;
    private final PackTemplateMetadataParser metadataParser;

    public PackTemplateValidator(DocxVariableParser docxVariableParser, PackTemplateMetadataParser metadataParser) {
        this.docxVariableParser = docxVariableParser;
        this.metadataParser = metadataParser;
    }

    public List<PackValidationIssue> validate(
            String templateCode,
            String templateFilePath,
            byte[] docxBytes,
            String metadataFilePath,
            byte[] metadataUtf8Json
    ) {
        List<PackValidationIssue> issues = new ArrayList<>();

        List<PackValidationIssue> docxIssues = validateDocxStructure(templateFilePath, templateCode, docxBytes);
        issues.addAll(docxIssues);
        boolean docxBlocking = docxIssues.stream()
                .anyMatch(i -> i.severity() == PackValidationSeverity.ERROR);

        List<PackValidationIssue> metadataIssues =
                metadataParser.validate(metadataFilePath, templateCode, metadataUtf8Json);
        issues.addAll(metadataIssues);
        boolean metadataBlocking = metadataIssues.stream()
                .anyMatch(i -> i.severity() == PackValidationSeverity.ERROR);

        if (docxBlocking || metadataBlocking) {
            return List.copyOf(issues);
        }

        Set<String> docxKeys;
        try {
            List<DetectedVariable> detected = docxVariableParser.parse(docxBytes);
            docxKeys = new LinkedHashSet<>();
            for (DetectedVariable variable : detected) {
                docxKeys.add(variable.key());
            }
        } catch (ResponseStatusException ex) {
            issues.add(PackValidationIssue.error("PACK_TEMPLATE_INVALID", "error.pack.template_invalid")
                    .withFile(templateFilePath)
                    .withTemplateCode(templateCode));
            return List.copyOf(issues);
        }

        PackTemplateMetadata metadata = metadataParser.parse(metadataUtf8Json);
        Set<String> metadataKeys = new LinkedHashSet<>();
        if (metadata.variables() != null) {
            for (PackTemplateMetadata.Variable variable : metadata.variables()) {
                if (variable != null && StringUtils.hasText(variable.key())) {
                    metadataKeys.add(variable.key().trim());
                }
            }
        }

        for (String key : docxKeys) {
            if (isSystemKey(key)) {
                continue;
            }
            if (!metadataKeys.contains(key)) {
                issues.add(PackValidationIssue.error(
                                "PACK_TEMPLATE_UNDECLARED_VARIABLE",
                                "error.pack.template_undeclared_variable")
                        .withFile(templateFilePath)
                        .withTemplateCode(templateCode)
                        .withVariable(key));
            }
        }

        for (String key : metadataKeys) {
            if (!docxKeys.contains(key)) {
                issues.add(PackValidationIssue.warning(
                                "PACK_TEMPLATE_UNUSED_VARIABLE",
                                "error.pack.template_unused_variable")
                        .withFile(metadataFilePath)
                        .withTemplateCode(templateCode)
                        .withVariable(key));
            }
        }

        return List.copyOf(issues);
    }

    /**
     * Minimal OOXML checks (PRD §138) + external relationship warnings (PRD §140).
     */
    public List<PackValidationIssue> validateDocxStructure(
            String templateFilePath,
            String templateCode,
            byte[] docxBytes
    ) {
        List<PackValidationIssue> issues = new ArrayList<>();
        if (docxBytes == null || docxBytes.length == 0) {
            issues.add(invalidDocx(templateFilePath, templateCode));
            return issues;
        }

        if (!looksLikeZip(docxBytes)) {
            issues.add(invalidDocx(templateFilePath, templateCode));
            return issues;
        }

        Set<String> entryNames = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(normalizeZipName(entry.getName()));
                zis.closeEntry();
            }
        } catch (IOException ex) {
            issues.add(invalidDocx(templateFilePath, templateCode));
            return issues;
        }

        if (!entryNames.contains("[content_types].xml") || !entryNames.contains("word/document.xml")) {
            issues.add(invalidDocx(templateFilePath, templateCode));
            return issues;
        }

        issues.addAll(detectExternalRelationships(templateFilePath, templateCode, docxBytes));
        return issues;
    }

    private List<PackValidationIssue> detectExternalRelationships(
            String templateFilePath,
            String templateCode,
            byte[] docxBytes
    ) {
        List<PackValidationIssue> issues = new ArrayList<>();
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(docxBytes))) {
            for (PackagePart part : pkg.getParts()) {
                for (PackageRelationship rel : part.getRelationships()) {
                    if (rel.getTargetMode() == TargetMode.EXTERNAL) {
                        issues.add(PackValidationIssue.warning(
                                        "PACK_TEMPLATE_INVALID",
                                        "error.pack.template_external_relationship")
                                .withFile(templateFilePath)
                                .withTemplateCode(templateCode));
                        return issues;
                    }
                }
            }
        } catch (Exception ex) {
            // Structure already validated; relationship scan is best-effort.
        }
        return issues;
    }

    private static PackValidationIssue invalidDocx(String templateFilePath, String templateCode) {
        return PackValidationIssue.error("PACK_TEMPLATE_INVALID", "error.pack.template_invalid")
                .withFile(templateFilePath)
                .withTemplateCode(templateCode);
    }

    private static boolean isSystemKey(String key) {
        return key != null && key.toLowerCase(Locale.ROOT).startsWith("system.");
    }

    private static boolean looksLikeZip(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 0x50
                && bytes[1] == 0x4B
                && (bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07)
                && (bytes[3] == 0x04 || bytes[3] == 0x06 || bytes[3] == 0x08);
    }

    private static String normalizeZipName(String name) {
        String n = name.replace('\\', '/');
        if (n.startsWith("/")) {
            n = n.substring(1);
        }
        return n.toLowerCase(Locale.ROOT);
    }
}
