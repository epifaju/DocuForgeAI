package ai.docuforge.businesspack.lifecycle;

import ai.docuforge.audit.AuditActions;
import ai.docuforge.audit.AuditService;
import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.dto.PackSummaryResponse;
import ai.docuforge.businesspack.dto.PackTemplateResponse;
import ai.docuforge.businesspack.dto.PackUninstallResponse;
import ai.docuforge.businesspack.query.PackQueryMapper;
import ai.docuforge.config.PackProperties;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackInstallation;
import ai.docuforge.domain.businesspack.BusinessPackInstallationRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.BusinessPackTemplate;
import ai.docuforge.domain.businesspack.BusinessPackTemplateRepository;
import ai.docuforge.domain.businesspack.BusinessPackVersion;
import ai.docuforge.domain.businesspack.PackInstallationStatus;
import ai.docuforge.domain.document.GeneratedDocumentRepository;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateOrigin;
import ai.docuforge.domain.template.TemplateRepository;
import ai.docuforge.domain.template.TemplateStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pack / pack-template enable, disable & soft uninstall (PRD §§87–89, §§94–96).
 * Preserves rows, storage keys and historical documents.
 */
@Service
public class PackLifecycleService {

    static final String META_CASCADED_TEMPLATE_IDS = "cascadedTemplateIds";

    private final PackProperties packProperties;
    private final BusinessPackRepository packRepository;
    private final BusinessPackInstallationRepository installationRepository;
    private final BusinessPackTemplateRepository packTemplateRepository;
    private final TemplateRepository templateRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PackLifecycleService(
            PackProperties packProperties,
            BusinessPackRepository packRepository,
            BusinessPackInstallationRepository installationRepository,
            BusinessPackTemplateRepository packTemplateRepository,
            TemplateRepository templateRepository,
            GeneratedDocumentRepository generatedDocumentRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.packProperties = packProperties;
        this.packRepository = packRepository;
        this.installationRepository = installationRepository;
        this.packTemplateRepository = packTemplateRepository;
        this.templateRepository = templateRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PackSummaryResponse enablePack(DocuForgePrincipal principal, UUID packId) {
        assertPacksEnabled();
        BusinessPack pack = requirePack(principal.getCompanyId(), packId);
        assertNotUninstalled(pack);

        if (pack.getStatus() == BusinessPackStatus.INSTALLED
                || pack.getStatus() == BusinessPackStatus.UPDATE_AVAILABLE) {
            return PackQueryMapper.toSummary(pack);
        }
        if (pack.getStatus() != BusinessPackStatus.DISABLED) {
            throw conflict("error.pack.enable_not_allowed");
        }

        pack.setStatus(BusinessPackStatus.INSTALLED);
        packRepository.save(pack);

        BusinessPackInstallation installation = latestInstallation(principal.getCompanyId(), pack.getId());
        if (installation != null) {
            List<UUID> cascaded = readCascadedIds(installation.getMetadata());
            restoreCascadedTemplates(cascaded);
            installation.setStatus(PackInstallationStatus.ACTIVE);
            installation.setDisabledAt(null);
            installation.setMetadata(clearCascadedIds(installation.getMetadata()));
            installationRepository.save(installation);
        }

        auditService.record(
                principal,
                AuditActions.PACK_ENABLED,
                "BUSINESS_PACK",
                pack.getId().toString(),
                "SUCCESS",
                Map.of("packKey", pack.getPackKey())
        );
        return PackQueryMapper.toSummary(pack);
    }

    @Transactional
    public PackSummaryResponse disablePack(DocuForgePrincipal principal, UUID packId) {
        assertPacksEnabled();
        BusinessPack pack = requirePack(principal.getCompanyId(), packId);
        assertNotUninstalled(pack);

        if (pack.getStatus() == BusinessPackStatus.DISABLED) {
            return PackQueryMapper.toSummary(pack);
        }

        List<Template> activePackTemplates = templateRepository
                .findByCompany_IdAndSourcePack_Id(principal.getCompanyId(), pack.getId())
                .stream()
                .filter(t -> t.getStatus() == TemplateStatus.ACTIVE)
                .toList();

        List<UUID> cascadedIds = new ArrayList<>();
        for (Template template : activePackTemplates) {
            template.setStatus(TemplateStatus.DRAFT);
            templateRepository.save(template);
            cascadedIds.add(template.getId());
        }

        pack.setStatus(BusinessPackStatus.DISABLED);
        packRepository.save(pack);

        BusinessPackInstallation installation = latestInstallation(principal.getCompanyId(), pack.getId());
        if (installation != null) {
            installation.setStatus(PackInstallationStatus.DISABLED);
            installation.setDisabledAt(Instant.now());
            installation.setMetadata(writeCascadedIds(installation.getMetadata(), cascadedIds));
            installationRepository.save(installation);
        }

        auditService.record(
                principal,
                AuditActions.PACK_DISABLED,
                "BUSINESS_PACK",
                pack.getId().toString(),
                "SUCCESS",
                Map.of(
                        "packKey", pack.getPackKey(),
                        "cascadedTemplates", cascadedIds.size()
                )
        );
        return PackQueryMapper.toSummary(pack);
    }

    @Transactional
    public PackTemplateResponse enableTemplate(
            DocuForgePrincipal principal,
            UUID packId,
            String templateCode
    ) {
        assertPacksEnabled();
        BusinessPack pack = requirePack(principal.getCompanyId(), packId);
        assertNotUninstalled(pack);
        if (pack.getStatus() == BusinessPackStatus.DISABLED) {
            throw conflict("error.pack.disabled");
        }

        BusinessPackTemplate link = requirePackTemplate(pack, templateCode);
        Template template = requirePackOwnedTemplate(principal.getCompanyId(), pack, link);

        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            throw conflict("error.pack.template_archived");
        }
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            template.setStatus(TemplateStatus.ACTIVE);
            templateRepository.save(template);
        }

        auditService.record(
                principal,
                AuditActions.PACK_TEMPLATE_ENABLED,
                "TEMPLATE",
                template.getId().toString(),
                "SUCCESS",
                Map.of(
                        "packKey", pack.getPackKey(),
                        "templateCode", template.getCode()
                )
        );
        return PackQueryMapper.toTemplate(link);
    }

    @Transactional
    public PackTemplateResponse disableTemplate(
            DocuForgePrincipal principal,
            UUID packId,
            String templateCode
    ) {
        assertPacksEnabled();
        BusinessPack pack = requirePack(principal.getCompanyId(), packId);
        assertNotUninstalled(pack);

        BusinessPackTemplate link = requirePackTemplate(pack, templateCode);
        Template template = requirePackOwnedTemplate(principal.getCompanyId(), pack, link);

        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            throw conflict("error.pack.template_archived");
        }
        if (template.getStatus() == TemplateStatus.ACTIVE) {
            template.setStatus(TemplateStatus.DRAFT);
            templateRepository.save(template);
        }

        auditService.record(
                principal,
                AuditActions.PACK_TEMPLATE_DISABLED,
                "TEMPLATE",
                template.getId().toString(),
                "SUCCESS",
                Map.of(
                        "packKey", pack.getPackKey(),
                        "templateCode", template.getCode()
                )
        );
        return PackQueryMapper.toTemplate(link);
    }

    /**
     * Logical uninstall (PRD §§94–96): never hard-deletes pack/version/template rows or storage.
     * Pack templates become unavailable for new generations; historical documents stay readable.
     */
    @Transactional
    public PackUninstallResponse uninstallPack(DocuForgePrincipal principal, UUID packId) {
        assertPacksEnabled();
        BusinessPack pack = requirePack(principal.getCompanyId(), packId);

        long historicalDocs = generatedDocumentRepository.countByCompanyIdAndTemplateSourcePackId(
                principal.getCompanyId(),
                pack.getId()
        );

        if (pack.getStatus() == BusinessPackStatus.UNINSTALLED) {
            int archived = (int) templateRepository
                    .findByCompany_IdAndSourcePack_Id(principal.getCompanyId(), pack.getId())
                    .stream()
                    .filter(t -> t.getStatus() == TemplateStatus.ARCHIVED)
                    .count();
            return new PackUninstallResponse(
                    pack.getId(),
                    pack.getPackKey(),
                    BusinessPackStatus.UNINSTALLED,
                    archived,
                    historicalDocs,
                    true
            );
        }

        List<Template> packTemplates = templateRepository
                .findByCompany_IdAndSourcePack_Id(principal.getCompanyId(), pack.getId());
        int archived = 0;
        for (Template template : packTemplates) {
            if (template.getOrigin() != TemplateOrigin.PACK) {
                continue;
            }
            if (template.getStatus() != TemplateStatus.ARCHIVED) {
                template.setStatus(TemplateStatus.ARCHIVED);
                templateRepository.save(template);
                archived++;
            }
        }

        pack.setStatus(BusinessPackStatus.UNINSTALLED);
        packRepository.save(pack);

        Instant now = Instant.now();
        BusinessPackInstallation installation = latestInstallation(principal.getCompanyId(), pack.getId());
        if (installation != null) {
            installation.setStatus(PackInstallationStatus.UNINSTALLED);
            installation.setUninstalledAt(now);
            if (installation.getDisabledAt() == null) {
                installation.setDisabledAt(now);
            }
            installation.setMetadata(clearCascadedIds(installation.getMetadata()));
            installationRepository.save(installation);
        }

        auditService.record(
                principal,
                AuditActions.PACK_UNINSTALLED,
                "BUSINESS_PACK",
                pack.getId().toString(),
                "SUCCESS",
                Map.of(
                        "packKey", pack.getPackKey(),
                        "templatesArchived", archived,
                        "historicalDocumentCount", historicalDocs,
                        "metadataPreserved", true
                )
        );

        return new PackUninstallResponse(
                pack.getId(),
                pack.getPackKey(),
                BusinessPackStatus.UNINSTALLED,
                archived,
                historicalDocs,
                true
        );
    }

    private void restoreCascadedTemplates(List<UUID> cascadedIds) {
        for (UUID templateId : cascadedIds) {
            templateRepository.findById(templateId).ifPresent(template -> {
                if (template.getStatus() == TemplateStatus.DRAFT) {
                    template.setStatus(TemplateStatus.ACTIVE);
                    templateRepository.save(template);
                }
            });
        }
    }

    private BusinessPackTemplate requirePackTemplate(BusinessPack pack, String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            throw notFound("error.pack.template_not_found");
        }
        BusinessPackVersion current = pack.getCurrentVersion();
        if (current == null) {
            throw notFound("error.pack.template_not_found");
        }
        return packTemplateRepository
                .findByBusinessPackVersionIdAndTemplateCode(current.getId(), templateCode.trim())
                .orElseThrow(() -> notFound("error.pack.template_not_found"));
    }

    private Template requirePackOwnedTemplate(
            UUID companyId,
            BusinessPack pack,
            BusinessPackTemplate link
    ) {
        Template template = link.getTemplate();
        if (template == null
                || !companyId.equals(template.getCompany().getId())
                || template.getOrigin() != TemplateOrigin.PACK
                || template.getSourcePack() == null
                || !pack.getId().equals(template.getSourcePack().getId())) {
            throw notFound("error.pack.template_not_found");
        }
        return template;
    }

    private BusinessPackInstallation latestInstallation(UUID companyId, UUID packId) {
        return installationRepository
                .findFirstByCompanyIdAndBusinessPackIdOrderByInstalledAtDesc(companyId, packId)
                .orElse(null);
    }

    private BusinessPack requirePack(UUID companyId, UUID packId) {
        return packRepository.findByIdAndCompanyIdWithCurrentVersion(packId, companyId)
                .orElseThrow(() -> notFound("error.pack.not_found"));
    }

    private static void assertNotUninstalled(BusinessPack pack) {
        if (pack.getStatus() == BusinessPackStatus.UNINSTALLED) {
            throw conflict("error.pack.uninstalled");
        }
    }

    private List<UUID> readCascadedIds(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            JsonNode arr = root.get(META_CASCADED_TEMPLATE_IDS);
            if (arr == null || !arr.isArray()) {
                return List.of();
            }
            List<UUID> ids = new ArrayList<>();
            for (JsonNode node : arr) {
                if (node != null && node.isTextual()) {
                    ids.add(UUID.fromString(node.asText()));
                }
            }
            return ids;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeCascadedIds(String existingMetadata, List<UUID> cascadedIds) {
        try {
            ObjectNode root = parseOrCreateObject(existingMetadata);
            ArrayNode array = root.putArray(META_CASCADED_TEMPLATE_IDS);
            for (UUID id : cascadedIds) {
                array.add(id.toString());
            }
            return objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "error.pack.lifecycle_failed", ex);
        }
    }

    private String clearCascadedIds(String existingMetadata) {
        try {
            ObjectNode root = parseOrCreateObject(existingMetadata);
            root.remove(META_CASCADED_TEMPLATE_IDS);
            if (root.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            return existingMetadata;
        }
    }

    private ObjectNode parseOrCreateObject(String metadataJson) throws IOException {
        if (!StringUtils.hasText(metadataJson)) {
            return objectMapper.createObjectNode();
        }
        JsonNode node = objectMapper.readTree(metadataJson);
        if (node != null && node.isObject()) {
            return (ObjectNode) node;
        }
        return objectMapper.createObjectNode();
    }

    private void assertPacksEnabled() {
        if (!packProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.pack.feature_disabled");
        }
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
