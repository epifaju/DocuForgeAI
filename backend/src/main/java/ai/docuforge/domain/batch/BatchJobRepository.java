package ai.docuforge.domain.batch;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchJobRepository extends JpaRepository<BatchJob, UUID> {

    @EntityGraph(attributePaths = {"templateVersion", "template"})
    Page<BatchJob> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    @Query("""
            select b from BatchJob b
            join fetch b.templateVersion
            left join fetch b.template
            where b.id = :id and b.company.id = :companyId
            """)
    Optional<BatchJob> findByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    long countByCompany_Id(UUID companyId);

    long countByCompany_IdAndStatusIn(UUID companyId, Collection<BatchJobStatus> statuses);
}
