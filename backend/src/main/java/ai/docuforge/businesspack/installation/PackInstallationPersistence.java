package ai.docuforge.businesspack.installation;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.dto.PackInstallRequest;
import ai.docuforge.businesspack.dto.PackInstallResponse;
import ai.docuforge.businesspack.manifest.PackManifest;
import ai.docuforge.businesspack.template.PackTemplateMetadata;
import ai.docuforge.businesspack.template.PackTemplateMetadataParser;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackFile;
import ai.docuforge.domain.businesspack.BusinessPackFileRepository;
import ai.docuforge.domain.businesspack.BusinessPackInstallation;
import ai.docuforge.domain.businesspack.BusinessPackInstallationRepository;
import ai.docuforge.domain.businesspack.BusinessPackPrompt;
import ai.docuforge.domain.businesspack.BusinessPackPromptRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.BusinessPackTemplate;
import ai.docuforge.domain.businesspack.BusinessPackTemplateRepository;
import ai.docuforge.domain.businesspack.BusinessPackType;
import ai.docuforge.domain.businesspack.BusinessPackVersion;
import ai.docuforge.domain.businesspack.BusinessPackVersionRepository;
import ai.docuforge.domain.businesspack.PackFileType;
import ai.docuforge.domain.businesspack.PackImportJob;
import ai.docuforge.domain.businesspack.PackImportJobRepository;
import ai.docuforge.domain.businesspack.PackImportJobStatus;
import ai.docuforge.domain.businesspack.PackInstallationStatus;
import ai.docuforge.domain.businesspack.PackInstallationType;
import ai.docuforge.domain.businesspack.PackVersionStatus;
import ai.docuforge.domain.company.Company;
import ai.docuforge.domain.company.CompanyRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateOrigin;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVariable;
import ai.docuforge.domain.template.TemplateVariableRepository;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.domain.template.TemplateVersionRepository;
import ai.docuforge.businesspack.checksum.PackChecksumValidator;
import ai.docuforge.businesspack.compatibility.PackCompatibilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * DB transaction boundary for pack installation (called after StorageProvider staging).
 */
@Service
public class PackInstallationPersistence {

    private final CompanyRepository companyRepository;
    private final BusinessPackRepository packRepository;
    private final BusinessPackVersionRepository packVersionRepository;
    private final BusinessPackTemplateRepository packTemplateRepository;
    private final BusinessPackPromptRepository packPromptRepository;
    private final BusinessPackFileRepository packFileRepository;
    private final BusinessPackInstallationRepository installationRepository;
    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateVariableRepository templateVariableRepository;
    private final PackImportJobRepository jobRepository;
    private final PackTemplateMetadataParser metadataParser;
    private final PackChecksumValidator checksumValidator;
    private final PackCompatibilityService compatibilityService;
    private final ObjectMapper objectMapper;

    public PackInstallationPersistence(
            CompanyRepository companyRepository,
            BusinessPackRepository packRepository,
            BusinessPackVersionRepository packVersionRepository,
            BusinessPackTemplateRepository packTemplateRepository,
            BusinessPackPromptRepository packPromptRepository,
            BusinessPackFileRepository packFileRepository,
            BusinessPackInstallationRepository installationRepository,
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateVariableRepository templateVariableRepository,
            PackImportJobRepository jobRepository,
            PackTemplateMetadataParser metadataParser,
            PackChecksumValidator checksumValidator,
            PackCompatibilityService compatibilityService,
            ObjectMapper objectMapper
    ) {
        this.companyRepository = companyRepository;
        this.packRepository = packRepository;
        this.packVersionRepository = packVersionRepository;
        this.packTemplateRepository = packTemplateRepository;
        this.packPromptRepository = packPromptRepository;
        this.packFileRepository = packFileRepository;
        this.installationRepository = installationRepository;
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.templateVariableRepository = templateVariableRepository;
        this.jobRepository = jobRepository;
        this.metadataParser = metadataParser;
        this.checksumValidator = checksumValidator;
        this.compatibilityService = compatibilityService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PackInstallResponse persist(
            DocuForgePrincipal principal,
            UUID jobId,
            PackInstallRequest options,
            PackManifest manifest,
            Map<String, byte[]> logicalBytes,
            Map<String, String> stagedByLogical,
            String archiveChecksum
    ) {
        PackImportJob job = jobRepository.findByIdAndCompanyId(jobId, principal.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.pack.import_not_found"));
        Company company = companyRepository.getReferenceById(principal.getCompanyId());
        String packKey = manifest.id();

        BusinessPack pack = packRepository.findByCompanyIdAndPackKey(company.getId(), packKey).orElse(null);
        PackInstallationType installationType = PackInstallationType.FRESH;
        if (pack != null) {
            var existingSameVersion = packVersionRepository.findByBusinessPackIdAndVersion(pack.getId(), manifest.version());
            if (existingSameVersion.isPresent()) {
                if (pack.getStatus() != BusinessPackStatus.UNINSTALLED) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "error.pack.version_already_installed");
                }
                return reinstallUninstalledVersion(
                        principal,
                        job,
                        company,
                        pack,
                        existingSameVersion.get(),
                        options,
                        manifest,
                        logicalBytes,
                        stagedByLogical,
                        archiveChecksum
                );
            }
            if (pack.getCurrentVersion() != null
                    && compatibilityService.isDowngrade(pack.getCurrentVersion().getVersion(), manifest.version())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "error.pack.downgrade_not_allowed");
            }
            installationType = PackInstallationType.UPDATE;
        } else {
            pack = new BusinessPack();
            pack.setCompany(company);
            pack.setPackKey(packKey);
            pack.setSlug(manifest.slug());
            pack.setName(manifest.name());
            pack.setDescription(manifest.description());
            pack.setPackType(manifest.type() == null ? BusinessPackType.CUSTOM : manifest.type());
            if (manifest.publisher() != null) {
                pack.setPublisherId(manifest.publisher().id());
                pack.setPublisherName(manifest.publisher().name());
            }
            pack.setStatus(Boolean.TRUE.equals(options.enablePack())
                    ? BusinessPackStatus.INSTALLED
                    : BusinessPackStatus.DISABLED);
            pack = packRepository.save(pack);
        }

        final UUID packId = pack.getId();
        if (manifest.templates() != null) {
            for (PackManifest.TemplateEntry entry : manifest.templates()) {
                if (entry == null || !StringUtils.hasText(entry.code())) {
                    continue;
                }
                templateRepository.findByCompanyIdAndCode(company.getId(), entry.code()).ifPresent(existing -> {
                    if (existing.getOrigin() != TemplateOrigin.PACK
                            || existing.getSourcePack() == null
                            || !existing.getSourcePack().getId().equals(packId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "error.pack.template_code_collision");
                    }
                });
            }
        }

        BusinessPackVersion packVersion = new BusinessPackVersion();
        packVersion.setBusinessPack(pack);
        packVersion.setVersion(manifest.version());
        packVersion.setSchemaVersion(manifest.schemaVersion());
        try {
            packVersion.setManifest(objectMapper.writeValueAsString(manifest));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.installation_failed", ex);
        }
        if (manifest.compatibility() != null) {
            packVersion.setMinimumDocuForgeVersion(manifest.compatibility().minimumDocuForgeVersion());
            packVersion.setMaximumDocuForgeVersion(manifest.compatibility().maximumDocuForgeVersion());
        }
        packVersion.setArchiveChecksum(archiveChecksum);
        packVersion.setStatus(PackVersionStatus.INSTALLED);
        packVersion.setInstalledAt(Instant.now());
        packVersion = packVersionRepository.save(packVersion);

        if (pack.getCurrentVersion() != null) {
            BusinessPackVersion previous = pack.getCurrentVersion();
            previous.setStatus(PackVersionStatus.SUPERSEDED);
            packVersionRepository.save(previous);
        }
        pack.setCurrentVersion(packVersion);
        pack.setName(manifest.name());
        pack.setDescription(manifest.description());
        pack.setSlug(manifest.slug());
        pack.setStatus(Boolean.TRUE.equals(options.enablePack())
                ? BusinessPackStatus.INSTALLED
                : BusinessPackStatus.DISABLED);
        packRepository.save(pack);

        ContentInstallCounts counts = installVersionContents(
                principal,
                company,
                pack,
                packVersion,
                manifest,
                logicalBytes,
                stagedByLogical,
                Boolean.TRUE.equals(options.enableTemplates())
        );

        BusinessPackInstallation installation = new BusinessPackInstallation();
        installation.setCompany(company);
        installation.setBusinessPack(pack);
        installation.setBusinessPackVersion(packVersion);
        installation.setInstallationType(installationType);
        installation.setInstalledBy(principal.getUserId());
        installation.setInstalledAt(Instant.now());
        installation.setStatus(Boolean.TRUE.equals(options.enablePack())
                ? PackInstallationStatus.ACTIVE
                : PackInstallationStatus.DISABLED);
        installation = installationRepository.save(installation);

        job.setStatus(PackImportJobStatus.INSTALLED);
        job.setCompletedAt(Instant.now());
        job.setDetectedPackKey(packKey);
        job.setDetectedVersion(manifest.version());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        jobRepository.save(job);

        return new PackInstallResponse(
                job.getId(),
                pack.getId(),
                packVersion.getId(),
                installation.getId(),
                packKey,
                manifest.version(),
                installationType,
                installation.getStatus(),
                counts.templatesInstalled(),
                counts.promptsInstalled()
        );
    }

    /**
     * Soft-uninstalled pack + same SemVer: refresh version contents from the imported ZIP (PRD §99 REINSTALL).
     */
    private PackInstallResponse reinstallUninstalledVersion(
            DocuForgePrincipal principal,
            PackImportJob job,
            Company company,
            BusinessPack pack,
            BusinessPackVersion packVersion,
            PackInstallRequest options,
            PackManifest manifest,
            Map<String, byte[]> logicalBytes,
            Map<String, String> stagedByLogical,
            String archiveChecksum
    ) {
        final UUID packId = pack.getId();
        if (manifest.templates() != null) {
            for (PackManifest.TemplateEntry entry : manifest.templates()) {
                if (entry == null || !StringUtils.hasText(entry.code())) {
                    continue;
                }
                templateRepository.findByCompanyIdAndCode(company.getId(), entry.code()).ifPresent(existing -> {
                    if (existing.getOrigin() != TemplateOrigin.PACK
                            || existing.getSourcePack() == null
                            || !existing.getSourcePack().getId().equals(packId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "error.pack.template_code_collision");
                    }
                });
            }
        }

        packTemplateRepository.deleteByBusinessPackVersionId(packVersion.getId());
        packPromptRepository.deleteByBusinessPackVersionId(packVersion.getId());
        packFileRepository.deleteByBusinessPackVersionId(packVersion.getId());
        packTemplateRepository.flush();
        packPromptRepository.flush();
        packFileRepository.flush();

        packVersion.setSchemaVersion(manifest.schemaVersion());
        try {
            packVersion.setManifest(objectMapper.writeValueAsString(manifest));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.installation_failed", ex);
        }
        if (manifest.compatibility() != null) {
            packVersion.setMinimumDocuForgeVersion(manifest.compatibility().minimumDocuForgeVersion());
            packVersion.setMaximumDocuForgeVersion(manifest.compatibility().maximumDocuForgeVersion());
        } else {
            packVersion.setMinimumDocuForgeVersion(null);
            packVersion.setMaximumDocuForgeVersion(null);
        }
        packVersion.setArchiveChecksum(archiveChecksum);
        packVersion.setStatus(PackVersionStatus.INSTALLED);
        packVersion.setInstalledAt(Instant.now());
        packVersion = packVersionRepository.save(packVersion);

        pack.setCurrentVersion(packVersion);
        pack.setName(manifest.name());
        pack.setDescription(manifest.description());
        pack.setSlug(manifest.slug());
        if (manifest.publisher() != null) {
            pack.setPublisherId(manifest.publisher().id());
            pack.setPublisherName(manifest.publisher().name());
        }
        pack.setStatus(Boolean.TRUE.equals(options.enablePack())
                ? BusinessPackStatus.INSTALLED
                : BusinessPackStatus.DISABLED);
        packRepository.save(pack);

        ContentInstallCounts counts = installVersionContents(
                principal,
                company,
                pack,
                packVersion,
                manifest,
                logicalBytes,
                stagedByLogical,
                Boolean.TRUE.equals(options.enableTemplates())
        );

        BusinessPackInstallation installation = new BusinessPackInstallation();
        installation.setCompany(company);
        installation.setBusinessPack(pack);
        installation.setBusinessPackVersion(packVersion);
        installation.setInstallationType(PackInstallationType.REINSTALL);
        installation.setInstalledBy(principal.getUserId());
        installation.setInstalledAt(Instant.now());
        installation.setStatus(Boolean.TRUE.equals(options.enablePack())
                ? PackInstallationStatus.ACTIVE
                : PackInstallationStatus.DISABLED);
        installation = installationRepository.save(installation);

        job.setStatus(PackImportJobStatus.INSTALLED);
        job.setCompletedAt(Instant.now());
        job.setDetectedPackKey(manifest.id());
        job.setDetectedVersion(manifest.version());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        jobRepository.save(job);

        return new PackInstallResponse(
                job.getId(),
                pack.getId(),
                packVersion.getId(),
                installation.getId(),
                manifest.id(),
                manifest.version(),
                PackInstallationType.REINSTALL,
                installation.getStatus(),
                counts.templatesInstalled(),
                counts.promptsInstalled()
        );
    }

    private ContentInstallCounts installVersionContents(
            DocuForgePrincipal principal,
            Company company,
            BusinessPack pack,
            BusinessPackVersion packVersion,
            PackManifest manifest,
            Map<String, byte[]> logicalBytes,
            Map<String, String> stagedByLogical,
            boolean enableTemplates
    ) {
        int templatesInstalled = 0;
        if (manifest.templates() != null) {
            for (PackManifest.TemplateEntry entry : manifest.templates()) {
                if (entry == null) {
                    continue;
                }
                installTemplate(
                        principal,
                        company,
                        pack,
                        packVersion,
                        manifest,
                        entry,
                        logicalBytes,
                        stagedByLogical,
                        enableTemplates
                );
                templatesInstalled++;
            }
        }

        int promptsInstalled = 0;
        if (manifest.prompts() != null) {
            for (PackManifest.PromptEntry prompt : manifest.prompts()) {
                if (prompt == null) {
                    continue;
                }
                byte[] bytes = logicalBytes.get(prompt.file());
                String content = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
                BusinessPackPrompt entity = new BusinessPackPrompt();
                entity.setBusinessPackVersion(packVersion);
                entity.setPromptCode(prompt.code());
                entity.setPromptVersion(prompt.version());
                entity.setContent(content);
                entity.setChecksum(checksumFor(manifest, prompt.file(), bytes));
                packPromptRepository.save(entity);
                promptsInstalled++;
            }
        }

        for (Map.Entry<String, byte[]> fileEntry : logicalBytes.entrySet()) {
            String logical = fileEntry.getKey();
            byte[] bytes = fileEntry.getValue();
            BusinessPackFile packFile = new BusinessPackFile();
            packFile.setBusinessPackVersion(packVersion);
            packFile.setLogicalPath(logical);
            packFile.setFileType(inferFileType(logical));
            packFile.setStorageKey(stagedByLogical.get(logical));
            packFile.setChecksum(checksumFor(manifest, logical, bytes));
            packFile.setSizeBytes(bytes == null ? 0L : bytes.length);
            packFileRepository.save(packFile);
        }

        return new ContentInstallCounts(templatesInstalled, promptsInstalled);
    }

    private record ContentInstallCounts(int templatesInstalled, int promptsInstalled) {
    }

    private void installTemplate(
            DocuForgePrincipal principal,
            Company company,
            BusinessPack pack,
            BusinessPackVersion packVersion,
            PackManifest manifest,
            PackManifest.TemplateEntry entry,
            Map<String, byte[]> logicalBytes,
            Map<String, String> stagedByLogical,
            boolean enableTemplates
    ) {
        Template template = templateRepository.findByCompanyIdAndCode(company.getId(), entry.code())
                .orElseGet(() -> {
                    Template created = new Template();
                    created.setCompany(company);
                    created.setCode(entry.code());
                    created.setName(entry.name());
                    created.setOrigin(TemplateOrigin.PACK);
                    created.setSourcePack(pack);
                    created.setStatus(enableTemplates ? TemplateStatus.ACTIVE : TemplateStatus.DRAFT);
                    return templateRepository.save(created);
                });

        template.setName(entry.name());
        template.setOrigin(TemplateOrigin.PACK);
        template.setSourcePack(pack);
        template.setStatus(enableTemplates ? TemplateStatus.ACTIVE : TemplateStatus.DRAFT);

        String storageKey = stagedByLogical.get(entry.templateFile());
        if (!StringUtils.hasText(storageKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.pack.file_missing");
        }

        int nextVersion = templateVersionRepository.findMaxVersionNumber(template.getId()) + 1;
        TemplateVersion version = new TemplateVersion();
        version.setTemplate(template);
        version.setVersionNumber(nextVersion);
        version.setOriginalFilename(fileName(entry.templateFile()));
        version.setStorageKey(storageKey);
        version.setChecksum(normalizeHex(checksumFor(manifest, entry.templateFile(), logicalBytes.get(entry.templateFile()))));
        version.setSourcePackVersion(packVersion);
        version.setSourceTemplateCode(entry.code());
        version.setCreatedBy(principal.getUserId());
        version = templateVersionRepository.save(version);

        template.setCurrentVersion(version);
        templateRepository.save(template);

        byte[] metadataBytes = logicalBytes.get(entry.metadataFile());
        if (metadataBytes != null) {
            PackTemplateMetadata metadata = metadataParser.parse(metadataBytes);
            persistVariables(version, metadata);
        }

        BusinessPackTemplate link = new BusinessPackTemplate();
        link.setBusinessPackVersion(packVersion);
        link.setTemplate(template);
        link.setTemplateVersion(version);
        link.setTemplateCode(entry.code());
        link.setEnabledByDefault(entry.enabledByDefault() == null || entry.enabledByDefault());
        packTemplateRepository.save(link);
    }

    private void persistVariables(TemplateVersion version, PackTemplateMetadata metadata) {
        if (metadata.variables() == null) {
            return;
        }
        List<TemplateVariable> entities = new ArrayList<>();
        int order = 0;
        for (PackTemplateMetadata.Variable variable : metadata.variables()) {
            if (variable == null || !StringUtils.hasText(variable.key())) {
                continue;
            }
            TemplateVariable entity = new TemplateVariable();
            entity.setTemplateVersion(version);
            entity.setVariableKey(variable.key().trim());
            entity.setLabel(variable.label());
            entity.setType(PackTemplateMetadataParser.mapDbpfType(variable.type()));
            entity.setRequired(Boolean.TRUE.equals(variable.required()));
            entity.setDisplayOrder(variable.order() == null ? order : variable.order());
            if (variable.defaultValue() != null) {
                entity.setDefaultValue(String.valueOf(variable.defaultValue()));
            }
            entity.setPlaceholder(variable.placeholder());
            entity.setConfiguration(buildConfiguration(variable));
            entities.add(entity);
            order++;
        }
        templateVariableRepository.saveAll(entities);
    }

    private String buildConfiguration(PackTemplateMetadata.Variable variable) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            if (variable.options() != null && !variable.options().isEmpty()) {
                ArrayNode options = root.putArray("options");
                for (PackTemplateMetadata.SelectOption option : variable.options()) {
                    if (option == null) {
                        continue;
                    }
                    ObjectNode opt = options.addObject();
                    opt.put("value", option.value());
                    opt.put("label", option.label());
                }
            }
            if (variable.validation() != null) {
                root.set("validation", objectMapper.valueToTree(variable.validation()));
            }
            if (variable.ai() != null) {
                root.set("ai", objectMapper.valueToTree(variable.ai()));
            }
            return root.isEmpty() ? null : objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            return null;
        }
    }

    private String checksumFor(PackManifest manifest, String logical, byte[] bytes) {
        if (manifest != null && manifest.checksums() != null && manifest.checksums().containsKey(logical)) {
            String normalized = checksumValidator.normalizeDeclared(manifest.checksums().get(logical));
            if (normalized != null) {
                return normalized;
            }
        }
        return checksumValidator.digestHex(bytes == null ? new byte[0] : bytes);
    }

    private static String normalizeHex(String checksum) {
        if (checksum == null) {
            return null;
        }
        String value = checksum.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("sha256:")) {
            return value.substring("sha256:".length());
        }
        return value;
    }

    private static PackFileType inferFileType(String logical) {
        String lower = logical.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            return PackFileType.TEMPLATE;
        }
        if (lower.endsWith(".json") && lower.startsWith("metadata/")) {
            return PackFileType.METADATA;
        }
        if (lower.startsWith("prompts/")) {
            return PackFileType.PROMPT;
        }
        if (lower.startsWith("samples/")) {
            return PackFileType.SAMPLE;
        }
        if (lower.startsWith("previews/")) {
            return PackFileType.PREVIEW;
        }
        if ("manifest.json".equals(lower)) {
            return PackFileType.MANIFEST;
        }
        return PackFileType.OTHER;
    }

    private static String fileName(String logicalPath) {
        String normalized = logicalPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }
}
