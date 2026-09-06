package ai.docuforge.businesspack.query;

import ai.docuforge.auth.security.DocuForgePrincipal;
import ai.docuforge.businesspack.dto.PackDetailResponse;
import ai.docuforge.businesspack.dto.PackInstallationResponse;
import ai.docuforge.businesspack.dto.PackPromptResponse;
import ai.docuforge.businesspack.dto.PackSummaryResponse;
import ai.docuforge.businesspack.dto.PackTemplateResponse;
import ai.docuforge.businesspack.dto.PackVersionResponse;
import ai.docuforge.common.api.PageResponse;
import ai.docuforge.config.PackProperties;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackInstallationRepository;
import ai.docuforge.domain.businesspack.BusinessPackPromptRepository;
import ai.docuforge.domain.businesspack.BusinessPackRepository;
import ai.docuforge.domain.businesspack.BusinessPackStatus;
import ai.docuforge.domain.businesspack.BusinessPackTemplateRepository;
import ai.docuforge.domain.businesspack.BusinessPackType;
import ai.docuforge.domain.businesspack.BusinessPackVersion;
import ai.docuforge.domain.businesspack.BusinessPackVersionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only ADMIN pack queries (PRD §§83–86).
 */
@Service
public class PackQueryService {

    private static final Set<String> SORTABLE = Set.of(
            "name",
            "packKey",
            "slug",
            "status",
            "packType",
            "createdAt",
            "updatedAt"
    );

    private final PackProperties packProperties;
    private final BusinessPackRepository packRepository;
    private final BusinessPackVersionRepository versionRepository;
    private final BusinessPackTemplateRepository packTemplateRepository;
    private final BusinessPackPromptRepository promptRepository;
    private final BusinessPackInstallationRepository installationRepository;

    public PackQueryService(
            PackProperties packProperties,
            BusinessPackRepository packRepository,
            BusinessPackVersionRepository versionRepository,
            BusinessPackTemplateRepository packTemplateRepository,
            BusinessPackPromptRepository promptRepository,
            BusinessPackInstallationRepository installationRepository
    ) {
        this.packProperties = packProperties;
        this.packRepository = packRepository;
        this.versionRepository = versionRepository;
        this.packTemplateRepository = packTemplateRepository;
        this.promptRepository = promptRepository;
        this.installationRepository = installationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PackSummaryResponse> list(
            DocuForgePrincipal principal,
            BusinessPackStatus status,
            BusinessPackType type,
            String search,
            int page,
            int size,
            String sort
    ) {
        assertPacksEnabled();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<BusinessPack> result = packRepository.search(
                principal.getCompanyId(),
                status,
                type,
                blankToNull(search),
                PageRequest.of(safePage, safeSize, resolveSort(sort))
        );
        List<PackSummaryResponse> items = result.getContent().stream()
                .map(PackQueryMapper::toSummary)
                .toList();
        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PackDetailResponse get(DocuForgePrincipal principal, UUID packId) {
        assertPacksEnabled();
        BusinessPack pack = requirePack(principal.getCompanyId(), packId);
        UUID currentVersionId = pack.getCurrentVersion() == null ? null : pack.getCurrentVersion().getId();

        List<PackVersionResponse> versions = versionRepository
                .findByBusinessPackIdOrderByCreatedAtDesc(pack.getId())
                .stream()
                .map(v -> PackQueryMapper.toVersion(v, currentVersionId))
                .toList();

        PackVersionResponse current = versions.stream()
                .filter(PackVersionResponse::current)
                .findFirst()
                .orElse(null);

        List<PackTemplateResponse> templates = listTemplatesForCurrent(pack);
        List<PackPromptResponse> prompts = listPromptsForCurrent(pack);
        PackInstallationResponse installation = installationRepository
                .findFirstByCompanyIdAndBusinessPackIdOrderByInstalledAtDesc(
                        principal.getCompanyId(), pack.getId())
                .map(PackQueryMapper::toInstallation)
                .orElse(null);

        return PackQueryMapper.toDetail(pack, current, versions, templates, prompts, installation);
    }

    @Transactional(readOnly = true)
    public List<PackVersionResponse> listVersions(DocuForgePrincipal principal, UUID packId) {
        assertPacksEnabled();
        BusinessPack pack = requirePack(principal.getCompanyId(), packId);
        UUID currentVersionId = pack.getCurrentVersion() == null ? null : pack.getCurrentVersion().getId();
        return versionRepository.findByBusinessPackIdOrderByCreatedAtDesc(pack.getId()).stream()
                .map(v -> PackQueryMapper.toVersion(v, currentVersionId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PackTemplateResponse> listTemplates(DocuForgePrincipal principal, UUID packId) {
        assertPacksEnabled();
        BusinessPack pack = requirePack(principal.getCompanyId(), packId);
        return listTemplatesForCurrent(pack);
    }

    private List<PackTemplateResponse> listTemplatesForCurrent(BusinessPack pack) {
        BusinessPackVersion current = pack.getCurrentVersion();
        if (current == null) {
            return List.of();
        }
        return packTemplateRepository.findByBusinessPackVersionId(current.getId()).stream()
                .map(PackQueryMapper::toTemplate)
                .toList();
    }

    private List<PackPromptResponse> listPromptsForCurrent(BusinessPack pack) {
        BusinessPackVersion current = pack.getCurrentVersion();
        if (current == null) {
            return List.of();
        }
        return promptRepository.findByBusinessPackVersionId(current.getId()).stream()
                .map(PackQueryMapper::toPrompt)
                .toList();
    }

    private BusinessPack requirePack(UUID companyId, UUID packId) {
        return packRepository.findByIdAndCompanyIdWithCurrentVersion(packId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.pack.not_found"));
    }

    private Sort resolveSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.DESC, "updatedAt");
        }
        String[] parts = sort.trim().split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.pack.invalid_sort");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
            direction = Sort.Direction.ASC;
        }
        return Sort.by(direction, field);
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void assertPacksEnabled() {
        if (!packProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.pack.feature_disabled");
        }
    }
}
