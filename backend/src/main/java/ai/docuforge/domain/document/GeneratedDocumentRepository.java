package ai.docuforge.domain.document;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GeneratedDocumentRepository
        extends JpaRepository<GeneratedDocument, UUID>, JpaSpecificationExecutor<GeneratedDocument> {

    Optional<GeneratedDocument> findByCompanyIdAndReference(UUID companyId, String reference);

    @Query("""
            select d from GeneratedDocument d
            join fetch d.template
            join fetch d.templateVersion
            where d.id = :id and d.company.id = :companyId
            """)
    Optional<GeneratedDocument> findByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    boolean existsByTemplateId(UUID templateId);

    @Query("""
            select count(d) from GeneratedDocument d
            where d.company.id = :companyId
              and d.template.sourcePack.id = :packId
            """)
    long countByCompanyIdAndTemplateSourcePackId(
            @Param("companyId") UUID companyId,
            @Param("packId") UUID packId
    );

    long countByCompanyId(UUID companyId);

    long countByCompany_IdAndCreatedAtGreaterThanEqual(UUID companyId, Instant from);

    long countByCompany_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID companyId,
            Instant from,
            Instant to
    );

    long countByCompany_IdAndStatus(UUID companyId, DocumentStatus status);

    @EntityGraph(attributePaths = "template")
    List<GeneratedDocument> findTop10ByCompany_IdOrderByCreatedAtDesc(UUID companyId);

    @Query("""
            select coalesce(max(d.documentVersionNumber), 0) from GeneratedDocument d
            where d.rootDocumentId = :rootDocumentId
            """)
    int findMaxDocumentVersionNumber(@Param("rootDocumentId") UUID rootDocumentId);

    @Query("""
            select d from GeneratedDocument d
            join fetch d.template
            join fetch d.templateVersion
            where d.company.id = :companyId and d.rootDocumentId = :rootDocumentId
            order by d.documentVersionNumber asc
            """)
    List<GeneratedDocument> findLineageByCompanyIdAndRootDocumentId(
            @Param("companyId") UUID companyId,
            @Param("rootDocumentId") UUID rootDocumentId
    );
}
