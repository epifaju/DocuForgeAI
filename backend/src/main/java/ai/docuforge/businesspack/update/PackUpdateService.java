package ai.docuforge.businesspack.update;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.archive.PackArchiveContentReader;
import ai.docuforge.businesspack.archive.PackArchiveContentReader.PackArchiveContents;
import ai.docuforge.businesspack.archive.PackArchiveInspector;
import ai.docuforge.businesspack.compatibility.PackCompatibilityService;
import ai.docuforge.businesspack.dto.PackUpdatePreviewResponse;
import ai.docuforge.businesspack.manifest.PackManifest;
import ai.docuforge.businesspack.manifest.PackManifestParser;
import ai.docuforge.businesspack.manifest.PackValidationIssue;
import ai.docuforge.businesspack.template.PackTemplateMetadata;
import ai.docuforge.businesspack.template.PackTemplateMetadataParser;
import ai.docuforge.config.PackProperties;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackPrompt;
import ai.docuforge.domain.businesspack.BusinessPackPromptRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackTemplate;
import ai.docuforge.domain.businesspack.BusinessPackTemplateRepository;
import ai.docuforge.domain.businesspack.BusinessPackVersion;
import ai.docuforge.domain.businesspack.PackImportJob;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.businesspack.PackImportJobStatus;
import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.storage.StorageProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Update detection and change preview for VALID import jobs (PRD §§90–93).
 * Actual version install / SUPERSEDED remains in {@link ai.docuforge.businesspack.installation.PackInstallationService}.
 */
@Service
public class PackUpdateService {

    private final PackProperties packProperties;
    private final PackImportJobRepository jobRepository;
    private final BusinessPackRepository packRepository;
    private final BusinessPackTemplateRepository packTemplateRepository;
    private final BusinessPackPromptRepository packPromptRepository;
    private final TemplateVariableRepository templateVariableRepository;
    private final StorageProvider storageProvider;
    private final PackArchiveInspector archiveInspector;
    private final PackArchiveContentReader contentReader;
    private final PackManifestParser manifestParser;
    private final PackTemplateMetadataParser metadataParser;
    private final PackCompatibilityService compatibilityService;
    private final PackChangeAnalyzer changeAnalyzer;

    public PackUpdateService(
            PackProperties packProperties,
            PackImportJobRepository jobRepository,
            BusinessPackRepository packRepository,
            BusinessPackTemplateRepository packTemplateRepository,
            BusinessPackPromptRepository packPromptRepository,
            TemplateVariableRepository templateVariableRepository,
            StorageProvider storageProvider,
            PackArchiveInspector archiveInspector,
            PackArchiveContentReader contentReader,
            PackManifestParser manifestParser,
            PackTemplateMetadataParser metadataParser,
            PackCompatibilityService compatibilityService,
            PackChangeAnalyzer changeAnalyzer
    ) {
        this.packProperties = packProperties;
        this.jobRepository = jobRepository;
        this.packRepository = packRepository;
        this.packTemplateRepository = packTemplateRepository;
        this.packPromptRepository = packPromptRepository;
        this.templateVariableRepository = templateVariableRepository;
        this.storageProvider = storageProvider;
        this.archiveInspector = archiveInspector;
        this.contentReader = contentReader;
        this.manifestParser = manifestParser;
        this.metadataParser = metadataParser;
        this.compatibilityService = compatibilityService;
        this.changeAnalyzer = changeAnalyzer;
    }

    @Transactional(readOnly = true)
    public PackUpdatePreviewResponse preview(DocuForgePrincipal principal, UUID jobId) {
        assertPacksEnabled();
        PackImportJob job = jobRepository.findByIdAndCompanyId(jobId, principal.getCompanyId())
                .orElseThrow(() -> notFound("error.pack.import_not_found"));

        if (job.getStatus() != PackImportJobStatus.VALID
                && job.getStatus() != PackImportJobStatus.INSTALLED) {
            throw conflict("error.pack.update_preview_requires_valid");
        }
        if (!StringUtils.hasText(job.getStagingStorageKey()) || !storageProvider.exists(job.getStagingStorageKey())) {
            if (job.getStatus() == PackImportJobStatus.INSTALLED) {
                throw conflict("error.pack.import_already_processed");
            }
            throw notFound("error.pack.file_missing");
        }

        Path tempZip = null;
        try {
            tempZip = Files.createTempFile("pack-update-preview-", ".zip");
            try (InputStream in = storageProvider.read(job.getStagingStorageKey())) {
                Files.copy(in, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }

            PackManifest candidateManifest;
            PackVersionSnapshot candidateSnapshot;
            try (ZipFile zipFile = ZipFile.builder().setPath(tempZip).get()) {
                var inspection = archiveInspector.inspect(tempZip);
                PackArchiveContents contents = contentReader.open(zipFile, inspection.entryNames());
                String manifestJson = contents.readUtf8("manifest.json");
                if (!StringUtils.hasText(manifestJson)) {
                    throw badRequest("error.pack.manifest_missing");
                }
                candidateManifest = manifestParser.parse(manifestJson);
                candidateSnapshot = snapshotFromArchive(candidateManifest, contents);
            }

            BusinessPack pack = packRepository
                    .findByCompanyIdAndPackKeyWithCurrentVersion(principal.getCompanyId(), candidateManifest.id())
                    .orElse(null);

            if (pack == null || pack.getCurrentVersion() == null) {
                return freshPreview(candidateManifest);
            }

            BusinessPackVersion installedVersion = pack.getCurrentVersion();
            String installed = installedVersion.getVersion();
            String candidate = candidateManifest.version();

            if (!compatibilityService.isUpgrade(installed, candidate)) {
                return new PackUpdatePreviewResponse(
                        false,
                        false,
                        pack.getId(),
                        pack.getPackKey(),
                        installed,
                        candidate,
                        compatibilityService.classifyUpdateKind(installed, candidate),
                        false,
                        emptySummary(),
                        List.of(),
                        List.of()
                );
            }

            PackVersionSnapshot installedSnapshot = snapshotFromInstalled(pack, installedVersion);
            PackChangeAnalysis analysis = changeAnalyzer.analyze(installedSnapshot, candidateSnapshot);
            String updateKind = compatibilityService.classifyUpdateKind(installed, candidate);
            List<PackValidationIssue> breaking = changeAnalyzer.withSemverBreakingGuard(analysis, updateKind);
            boolean semverMismatch = breaking.stream()
                    .anyMatch(i -> "PACK_SEMVER_BREAKING_CHANGE".equals(i.code()));

            return new PackUpdatePreviewResponse(
                    true,
                    false,
                    pack.getId(),
                    pack.getPackKey(),
                    installed,
                    candidate,
                    updateKind,
                    semverMismatch,
                    new PackUpdatePreviewResponse.ChangeSummary(
                            analysis.templatesAdded(),
                            analysis.templatesUpdated(),
                            analysis.templatesRemoved(),
                            analysis.variablesAdded(),
                            analysis.variablesRemoved(),
                            analysis.requiredVariablesAdded(),
                            analysis.promptsChanged()
                    ),
                    analysis.changes(),
                    breaking
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.validation_failed", ex);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private PackUpdatePreviewResponse freshPreview(PackManifest candidateManifest) {
        return new PackUpdatePreviewResponse(
                false,
                true,
                null,
                candidateManifest.id(),
                null,
                candidateManifest.version(),
                null,
                false,
                emptySummary(),
                List.of(),
                List.of()
        );
    }

    private PackVersionSnapshot snapshotFromInstalled(BusinessPack pack, BusinessPackVersion version) {
        Map<String, PackManifest.TemplateEntry> manifestTemplates = new LinkedHashMap<>();
        Map<String, PackManifest.PromptEntry> manifestPrompts = new LinkedHashMap<>();
        if (StringUtils.hasText(version.getManifest())) {
            try {
                PackManifest installedManifest = manifestParser.parse(version.getManifest());
                if (installedManifest.templates() != null) {
                    for (PackManifest.TemplateEntry entry : installedManifest.templates()) {
                        if (entry != null && StringUtils.hasText(entry.code())) {
                            manifestTemplates.put(entry.code().trim(), entry);
                        }
                    }
                }
                if (installedManifest.prompts() != null) {
                    for (PackManifest.PromptEntry entry : installedManifest.prompts()) {
                        if (entry != null && StringUtils.hasText(entry.code())) {
                            manifestPrompts.put(entry.code().trim(), entry);
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // fall back to pack template / prompt rows
            }
        }

        List<BusinessPackTemplate> links = packTemplateRepository.findByBusinessPackVersionId(version.getId());
        List<PackVersionSnapshot.TemplateSnapshot> templates = new ArrayList<>();
        for (BusinessPackTemplate link : links) {
            List<TemplateVariable> vars = templateVariableRepository
                    .findByTemplateVersionIdOrderByDisplayOrderAsc(link.getTemplateVersion().getId());
            List<PackVersionSnapshot.VariableSnapshot> variableSnapshots = vars.stream()
                    .map(v -> new PackVersionSnapshot.VariableSnapshot(
                            v.getVariableKey(),
                            v.getType() == null ? null : v.getType().name(),
                            v.isRequired()
                    ))
                    .toList();
            PackManifest.TemplateEntry manifestEntry = manifestTemplates.get(link.getTemplateCode());
            String name = manifestEntry != null && StringUtils.hasText(manifestEntry.name())
                    ? manifestEntry.name()
                    : (link.getTemplate() == null ? link.getTemplateCode() : link.getTemplate().getName());
            String templateVersionLabel = manifestEntry != null ? manifestEntry.version() : null;
            templates.add(new PackVersionSnapshot.TemplateSnapshot(
                    link.getTemplateCode(),
                    name,
                    templateVersionLabel,
                    variableSnapshots
            ));
        }

        List<BusinessPackPrompt> prompts = packPromptRepository.findByBusinessPackVersionId(version.getId());
        List<PackVersionSnapshot.PromptSnapshot> promptSnapshots = prompts.stream()
                .map(p -> {
                    PackManifest.PromptEntry entry = manifestPrompts.get(p.getPromptCode());
                    String promptVersion = StringUtils.hasText(p.getPromptVersion())
                            ? p.getPromptVersion()
                            : (entry == null ? null : entry.version());
                    return new PackVersionSnapshot.PromptSnapshot(
                            p.getPromptCode(),
                            promptVersion,
                            p.getChecksum()
                    );
                })
                .toList();

        return new PackVersionSnapshot(pack.getPackKey(), version.getVersion(), templates, promptSnapshots);
    }

    private PackVersionSnapshot snapshotFromArchive(PackManifest manifest, PackArchiveContents contents)
            throws IOException {
        List<PackVersionSnapshot.TemplateSnapshot> templates = new ArrayList<>();
        if (manifest.templates() != null) {
            for (PackManifest.TemplateEntry entry : manifest.templates()) {
                if (entry == null || !StringUtils.hasText(entry.code())) {
                    continue;
                }
                List<PackVersionSnapshot.VariableSnapshot> variables = List.of();
                if (StringUtils.hasText(entry.metadataFile())) {
                    byte[] metadataBytes = contents.readBytes(entry.metadataFile());
                    if (metadataBytes != null) {
                        PackTemplateMetadata metadata = metadataParser.parse(metadataBytes);
                        if (metadata.variables() != null) {
                            variables = metadata.variables().stream()
                                    .filter(v -> v != null && StringUtils.hasText(v.key()))
                                    .map(v -> new PackVersionSnapshot.VariableSnapshot(
                                            v.key().trim(),
                                            v.type(),
                                            Boolean.TRUE.equals(v.required())
                                    ))
                                    .toList();
                        }
                    }
                }
                templates.add(new PackVersionSnapshot.TemplateSnapshot(
                        entry.code(),
                        entry.name(),
                        entry.version(),
                        variables
                ));
            }
        }

        List<PackVersionSnapshot.PromptSnapshot> prompts = new ArrayList<>();
        if (manifest.prompts() != null) {
            for (PackManifest.PromptEntry prompt : manifest.prompts()) {
                if (prompt == null || !StringUtils.hasText(prompt.code())) {
                    continue;
                }
                String checksum = null;
                if (manifest.checksums() != null && StringUtils.hasText(prompt.file())) {
                    checksum = manifest.checksums().get(prompt.file());
                }
                prompts.add(new PackVersionSnapshot.PromptSnapshot(
                        prompt.code(),
                        prompt.version(),
                        checksum
                ));
            }
        }

        return new PackVersionSnapshot(manifest.id(), manifest.version(), templates, prompts);
    }

    private static PackUpdatePreviewResponse.ChangeSummary emptySummary() {
        return new PackUpdatePreviewResponse.ChangeSummary(0, 0, 0, 0, 0, 0, 0);
    }

    private void assertPacksEnabled() {
        if (!packProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.pack.feature_disabled");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
