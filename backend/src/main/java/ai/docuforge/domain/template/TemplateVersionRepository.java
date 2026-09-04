package ai.docuforge.domain.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {

    List<TemplateVersion> findByTemplateIdOrderByVersionNumberDesc(UUID templateId);

    Optional<TemplateVersion> findByTemplateIdAndVersionNumber(UUID templateId, Integer versionNumber);

    @Query("select coalesce(max(v.versionNumber), 0) from TemplateVersion v where v.template.id = :templateId")
    int findMaxVersionNumber(@Param("templateId") UUID templateId);

    long countByTemplateId(UUID templateId);

    @Query("""
            select v from TemplateVersion v
            join fetch v.template t
            where v.id = :id and t.company.id = :companyId
            """)
    Optional<TemplateVersion> findByIdAndCompanyId(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId
    );
}