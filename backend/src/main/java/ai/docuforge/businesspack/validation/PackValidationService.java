package ai.docuforge.businesspack.validation;

import ai.docuforge.businesspack.archive.PackArchiveContentReader;
import ai.docuforge.businesspack.archive.PackArchiveContentReader.PackArchiveContents;
import ai.docuforge.businesspack.archive.PackArchiveException;
import ai.docuforge.businesspack.archive.PackArchiveInspection;
import ai.docuforge.businesspack.archive.PackArchiveInspector;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.businesspack.compatibility.PackCompatibilityService;
import ai.docuforge.businesspack.manifest.PackManifest;
import ai.docuforge.businesspack.manifest.PackManifestException;
import ai.docuforge.businesspack.manifest.PackManifestValidationResult;
import ai.docuforge.businesspack.manifest.PackManifestValidator;
import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.manifest.PackValidationSeverity;
import ai.docuforge.businesspack.template.PackTemplateValidator;
import ai.docuforge.security.antivirus.AntivirusScanner;
import ai.docuforge.storage.StorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Full DBPF-1 validation pipeline (PRD §65). No installation / no StorageProvider writes.
 */
@Service
public class PackValidationService {

    private static final Logger log = LoggerFactory.getLogger(PackValidationService.class);

    private final PackArchiveInspector archiveInspector;
    private final PackArchiveContentReader contentReader;
    private final PackManifestValidator manifestValidator;
    private final PackCompatibilityService compatibilityService;
    private final PackChecksumValidator checksumValidator;
    private final PackTemplateValidator templateValidator;
    private final AntivirusScanner antivirusScanner;
    private final ObjectMapper objectMapper;

    public PackValidationService(
            PackArchiveInspector archiveInspector,
            PackArchiveContentReader contentReader,
            PackManifestValidator manifestValidator,
            PackCompatibilityService compatibilityService,
            PackChecksumValidator checksumValidator,
            PackTemplateValidator templateValidator,
            AntivirusScanner antivirusScanner,
            ObjectMapper objectMapper
    ) {
        this.archiveInspector = archiveInspector;
        this.contentReader = contentReader;
        this.manifestValidator = manifestValidator;
        this.compatibilityService = compatibilityService;
        this.checksumValidator = checksumValidator;
        this.templateValidator = templateValidator;
        this.antivirusScanner = antivirusScanner;
        this.objectMapper = objectMapper;
    }

    public PackValidationReport validate(Path archivePath) {
        List<PackValidationIssue> issues = new ArrayList<>();

        try {
            antivirusScanner.scan(archivePath, archivePath == null ? "pack.zip" : archivePath.getFileName().toString());
        } catch (StorageException ex) {
            issues.add(PackValidationIssue.error(ex.getCode(), ex.getMessage()).withFile("archive"));
            return PackValidationReport.of(null, issues, 0, 0);
        }

        PackArchiveInspection inspection;
        try {
            inspection = archiveInspector.inspect(archivePath);
        } catch (PackArchiveException ex) {
            issues.add(PackValidationIssue.error(ex.getCode(), ex.getMessage()).withFile("archive"));
            return PackValidationReport.of(null, issues, 0, 0);
        }

        try (ZipFile zipFile = ZipFile.builder().setPath(archivePath).get()) {
            PackArchiveContents contents;
            try {
                contents = contentReader.open(zipFile, inspection.entryNames());
            } catch (PackArchiveException ex) {
                issues.add(PackValidationIssue.error(ex.getCode(), ex.getMessage()).withFile("archive"));
                return PackValidationReport.of(null, issues, 0, 0);
            }

            if (!contents.has("manifest.json")) {
                issues.add(PackValidationIssue.error("PACK_MANIFEST_MISSING", "error.pack.manifest_missing")
                        .withFile("manifest.json"));
                return PackValidationReport.of(null, issues, 0, 0);
            }

            String manifestJson;
            try {
                manifestJson = contents.readUtf8("manifest.json");
            } catch (IOException ex) {
                issues.add(PackValidationIssue.error("PACK_MANIFEST_INVALID_JSON", "error.pack.manifest_invalid_json")
                        .withFile("manifest.json"));
                return PackValidationReport.of(null, issues, 0, 0);
            }

            PackManifestValidationResult manifestResult;
            try {
                manifestResult = manifestValidator.validate(manifestJson);
            } catch (PackManifestException ex) {
                issues.add(PackValidationIssue.error(ex.getCode(), ex.getMessage()).withFile("manifest.json"));
                return PackValidationReport.of(null, issues, 0, 0);
            }
            issues.addAll(manifestResult.issues());

            PackManifest manifest = manifestResult.manifest();
            PackValidationReport.PackIdentity identity = null;
            int templates = 0;
            int prompts = 0;
            if (manifest != null) {
                identity = new PackValidationReport.PackIdentity(manifest.id(), manifest.name(), manifest.version());
                templates = manifest.templates() == null ? 0 : manifest.templates().size();
                prompts = manifest.prompts() == null ? 0 : manifest.prompts().size();
            }

            if (hasErrors(issues) || manifest == null) {
                return PackValidationReport.of(identity, issues, templates, prompts);
            }

            issues.addAll(compatibilityService.validatePlatformCompatibility(manifest.compatibility()));
            if (hasErrors(issues)) {
                return PackValidationReport.of(identity, issues, templates, prompts);
            }

            Set<String> requiredPaths = collectRequiredPaths(manifest);
            Map<String, byte[]> contentsByPath = new LinkedHashMap<>();
            for (String path : requiredPaths) {
                if (!contents.has(path)) {
                    issues.add(PackValidationIssue.error("PACK_FILE_MISSING", "error.pack.file_missing")
                            .withFile(path));
                    continue;
                }
                try {
                    byte[] bytes = contents.readBytes(path);
                    if (bytes == null) {
                        issues.add(PackValidationIssue.error("PACK_FILE_MISSING", "error.pack.file_missing")
                                .withFile(path));
                    } else {
                        contentsByPath.put(path, bytes);
                    }
                } catch (IOException ex) {
                    issues.add(PackValidationIssue.error("PACK_FILE_MISSING", "error.pack.file_missing")
                            .withFile(path));
                }
            }
            if (hasErrors(issues)) {
                return PackValidationReport.of(identity, issues, templates, prompts);
            }

            issues.addAll(checksumValidator.validateContents(manifest.checksums(), contentsByPath));
            if (hasErrors(issues)) {
                return PackValidationReport.of(identity, issues, templates, prompts);
            }

            if (manifest.templates() != null) {
                for (PackManifest.TemplateEntry template : manifest.templates()) {
                    if (template == null) {
                        continue;
                    }
                    byte[] docx = contentsByPath.get(template.templateFile());
                    byte[] metadata = contentsByPath.get(template.metadataFile());
                    issues.addAll(templateValidator.validate(
                            template.code(),
                            template.templateFile(),
                            docx,
                            template.metadataFile(),
                            metadata
                    ));
                }
            }

            validatePrompts(manifest, contentsByPath, issues);
            validateSamples(manifest, contentsByPath, issues);

            return PackValidationReport.of(identity, issues, templates, prompts);
        } catch (IOException ex) {
            log.warn("Failed to open pack archive for content validation", ex);
            issues.add(PackValidationIssue.error("PACK_ARCHIVE_INVALID", "error.pack.archive_invalid")
                    .withFile("archive"));
            return PackValidationReport.of(null, issues, 0, 0);
        }
    }

    private void validatePrompts(
            PackManifest manifest,
            Map<String, byte[]> contentsByPath,
            List<PackValidationIssue> issues
    ) {
        if (manifest.prompts() == null) {
            return;
        }
        for (PackManifest.PromptEntry prompt : manifest.prompts()) {
            if (prompt == null || !StringUtils.hasText(prompt.file())) {
                continue;
            }
            byte[] bytes = contentsByPath.get(prompt.file());
            if (bytes == null || bytes.length == 0) {
                issues.add(PackValidationIssue.error("PACK_PROMPT_MISSING", "error.pack.prompt_missing")
                        .withFile(prompt.file())
                        .withTemplateCode(prompt.code()));
                continue;
            }
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                issues.add(PackValidationIssue.error("PACK_PROMPT_MISSING", "error.pack.prompt_missing")
                        .withFile(prompt.file())
                        .withTemplateCode(prompt.code()));
            }
        }
    }

    private void validateSamples(
            PackManifest manifest,
            Map<String, byte[]> contentsByPath,
            List<PackValidationIssue> issues
    ) {
        if (manifest.samples() == null) {
            return;
        }
        for (PackManifest.SampleEntry sample : manifest.samples()) {
            if (sample == null || !StringUtils.hasText(sample.file())) {
                continue;
            }
            byte[] bytes = contentsByPath.get(sample.file());
            if (bytes == null || bytes.length == 0) {
                issues.add(PackValidationIssue.error("PACK_FILE_MISSING", "error.pack.file_missing")
                        .withFile(sample.file()));
                continue;
            }
            if ("JSON".equalsIgnoreCase(sample.type())) {
                try {
                    objectMapper.readTree(bytes);
                } catch (IOException ex) {
                    issues.add(PackValidationIssue.error("PACK_TEMPLATE_INVALID", "error.pack.sample_invalid")
                            .withFile(sample.file())
                            .withTemplateCode(sample.templateCode()));
                }
            }
        }
    }

    private static Set<String> collectRequiredPaths(PackManifest manifest) {
        Set<String> paths = new LinkedHashSet<>();
        if (manifest.templates() != null) {
            for (PackManifest.TemplateEntry template : manifest.templates()) {
                if (template == null) {
                    continue;
                }
                if (StringUtils.hasText(template.templateFile())) {
                    paths.add(template.templateFile());
                }
                if (StringUtils.hasText(template.metadataFile())) {
                    paths.add(template.metadataFile());
                }
                if (StringUtils.hasText(template.previewFile())) {
                    paths.add(template.previewFile());
                }
            }
        }
        if (manifest.prompts() != null) {
            for (PackManifest.PromptEntry prompt : manifest.prompts()) {
                if (prompt != null && StringUtils.hasText(prompt.file())) {
                    paths.add(prompt.file());
                }
            }
        }
        if (manifest.samples() != null) {
            for (PackManifest.SampleEntry sample : manifest.samples()) {
                if (sample != null && StringUtils.hasText(sample.file())) {
                    paths.add(sample.file());
                }
            }
        }
        return paths;
    }

    private static boolean hasErrors(List<PackValidationIssue> issues) {
        return issues.stream().anyMatch(i -> i.severity() == PackValidationSeverity.ERROR);
    }
}
