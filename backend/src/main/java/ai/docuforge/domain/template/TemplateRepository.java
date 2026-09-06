package ai.docuforge.domain.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    Optional<Template> findByCompanyIdAndCode(UUID companyId, String code);

    Optional<Template> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Template> findByCompany_IdAndSourcePack_Id(UUID companyId, UUID sourcePackId);

    boolean existsByCompanyIdAndCode(UUID companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNot(UUID companyId, String code, UUID id);

    @Query("""
            select t from Template t
            left join fetch t.currentVersion
            left join fetch t.sourcePack
            where t.id = :id and t.company.id = :companyId
            """)
    Optional<Template> findByIdAndCompanyIdWithCurrentVersion(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId
    );

    @EntityGraph(attributePaths = "currentVersion")
    @Query("""
            select t from Template t
            where t.company.id = :companyId
              and (:status is null or t.status = :status)
              and (
                :q is null or :q = '' or
                lower(t.name) like lower(concat('%', cast(:q as string), '%')) or
                lower(t.code) like lower(concat('%', cast(:q as string), '%'))
              )
            """)
    Page<Template> search(
            @Param("companyId") UUID companyId,
            @Param("status") TemplateStatus status,
            @Param("q") String q,
            Pageable pageable
    );

    long countByCompany_IdAndStatus(UUID companyId, TemplateStatus status);
}