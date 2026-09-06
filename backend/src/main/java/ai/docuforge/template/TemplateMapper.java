package ai.docuforge.template;

import ai.docuforge.domain.template.Template;
import ai.docuforge.domain.template.TemplateVersion;
import ai.docuforge.template.dto.TemplateResponse;
import ai.docuforge.template.dto.TemplateVersionResponse;
import java.util.UUID;

final class TemplateMapper {

    private TemplateMapper() {
    }

    static TemplateResponse toResponse(Template template) {
        TemplateVersion current = template.getCurrentVersion();
        UUID sourcePackId = template.getSourcePack() == null ? null : template.getSourcePack().getId();
        return new TemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getCategory(),
                template.getStatus(),
                template.getOrigin(),
                sourcePackId,
                current == null ? null : current.getVersionNumber(),
                current == null ? null : current.getId(),
                template.getCreatedBy(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    static TemplateVersionResponse toVersionResponse(TemplateVersion version) {
        return new TemplateVersionResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getOriginalFilename(),
                version.getStorageKey(),
                version.getChecksum(),
                version.getCreatedBy(),
                version.getCreatedAt()
        );
    }
}