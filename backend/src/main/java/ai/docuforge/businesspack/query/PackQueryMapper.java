package ai.docuforge.businesspack.query;

import ai.docuforge.businesspack.dto.PackDetailResponse;
import ai.docuforge.businesspack.dto.PackInstallationResponse;
import ai.docuforge.businesspack.dto.PackPromptResponse;
import ai.docuforge.businesspack.dto.PackSummaryResponse;
import ai.docuforge.businesspack.dto.PackTemplateResponse;
import ai.docuforge.businesspack.dto.PackVersionResponse;
import ai.docuforge.domain.businesspack.BusinessPack;
import ai.docuforge.domain.businesspack.BusinessPackInstallation;
import ai.docuforge.domain.businesspack.BusinessPackPrompt;
import ai.docuforge.domain.businesspack.BusinessPackTemplate;
import ai.docuforge.domain.businesspack.BusinessPackVersion;
import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateStatus;
import ai.docuforge.domain.template.TemplateVersion;
import java.util.UUID;

public final class PackQueryMapper {

    private PackQueryMapper() {
    }

    public static PackSummaryResponse toSummary(BusinessPack pack) {
        BusinessPackVersion current = pack.getCurrentVersion();
        return new PackSummaryResponse(
                pack.getId(),
                pack.getPackKey(),
                pack.getSlug(),
                pack.getName(),
                pack.getDescription(),
                pack.getPackType(),
                pack.getPublisherId(),
                pack.getPublisherName(),
                pack.getStatus(),
                current == null ? null : current.getVersion(),
                current == null ? null : current.getId(),
                pack.getCreatedAt(),
                pack.getUpdatedAt()
        );
    }

    public static PackVersionResponse toVersion(BusinessPackVersion version, UUID currentVersionId) {
        boolean current = currentVersionId != null && currentVersionId.equals(version.getId());
        return new PackVersionResponse(
                version.getId(),
                version.getVersion(),
                version.getSchemaVersion(),
                version.getMinimumDocuForgeVersion(),
                version.getMaximumDocuForgeVersion(),
                version.getArchiveChecksum(),
                version.getStatus(),
                version.getInstalledAt(),
                version.getCreatedAt(),
                current
        );
    }

    public static PackTemplateResponse toTemplate(BusinessPackTemplate link) {
        Template template = link.getTemplate();
        TemplateVersion templateVersion = link.getTemplateVersion();
        TemplateStatus status = template == null ? null : template.getStatus();
        boolean enabled = status == TemplateStatus.ACTIVE;
        return new PackTemplateResponse(
                link.getId(),
                link.getTemplateCode(),
                template == null ? link.getTemplateCode() : template.getName(),
                status,
                template == null ? null : template.getId(),
                templateVersion == null ? null : templateVersion.getId(),
                templateVersion == null ? null : templateVersion.getVersionNumber(),
                link.isEnabledByDefault(),
                enabled
        );
    }

    public static PackPromptResponse toPrompt(BusinessPackPrompt prompt) {
        return new PackPromptResponse(
                prompt.getId(),
                prompt.getPromptCode(),
                prompt.getPromptVersion(),
                prompt.getContent(),
                prompt.getChecksum()
        );
    }

    public static PackInstallationResponse toInstallation(BusinessPackInstallation installation) {
        BusinessPackVersion version = installation.getBusinessPackVersion();
        return new PackInstallationResponse(
                installation.getId(),
                version == null ? null : version.getId(),
                version == null ? null : version.getVersion(),
                installation.getInstallationType(),
                installation.getStatus(),
                installation.getInstalledBy(),
                installation.getInstalledAt(),
                installation.getDisabledAt(),
                installation.getUninstalledAt()
        );
    }

    public static PackDetailResponse toDetail(
            BusinessPack pack,
            PackVersionResponse currentVersion,
            java.util.List<PackVersionResponse> versions,
            java.util.List<PackTemplateResponse> templates,
            java.util.List<PackPromptResponse> prompts,
            PackInstallationResponse installation
    ) {
        return new PackDetailResponse(
                pack.getId(),
                pack.getPackKey(),
                pack.getSlug(),
                pack.getName(),
                pack.getDescription(),
                pack.getPackType(),
                pack.getPublisherId(),
                pack.getPublisherName(),
                pack.getStatus(),
                currentVersion,
                versions,
                templates,
                prompts,
                installation,
                pack.getCreatedAt(),
                pack.getUpdatedAt()
        );
    }
}
