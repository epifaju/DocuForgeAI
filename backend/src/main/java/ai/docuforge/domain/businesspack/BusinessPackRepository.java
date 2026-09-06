package ai.docuforge.domain.businesspack;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessPackRepository extends JpaRepository<BusinessPack, UUID> {

    Optional<BusinessPack> findByCompanyIdAndPackKey(UUID companyId, String packKey);

    @Query("""
            select p from BusinessPack p
            left join fetch p.currentVersion
            where p.company.id = :companyId and p.packKey = :packKey
            """)
    Optional<BusinessPack> findByCompanyIdAndPackKeyWithCurrentVersion(
            @Param("companyId") UUID companyId,
            @Param("packKey") String packKey
    );

    Optional<BusinessPack> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<BusinessPack> findByCompanyIsNullAndPackKey(String packKey);

    boolean existsByCompanyIdAndPackKey(UUID companyId, String packKey);

    boolean existsByCompanyIsNullAndPackKey(String packKey);

    @EntityGraph(attributePaths = "currentVersion")
    @Query("""
            select p from BusinessPack p
            where p.company.id = :companyId
              and (:status is null or p.status = :status)
              and (:packType is null or p.packType = :packType)
              and (
                :search is null or :search = '' or
                lower(p.name) like lower(concat('%', cast(:search as string), '%')) or
                lower(p.packKey) like lower(concat('%', cast(:search as string), '%')) or
                lower(p.slug) like lower(concat('%', cast(:search as string), '%'))
              )
            """)
    Page<BusinessPack> search(
            @Param("companyId") UUID companyId,
            @Param("status") BusinessPackStatus status,
            @Param("packType") BusinessPackType packType,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select p from BusinessPack p
            left join fetch p.currentVersion
            where p.id = :id and p.company.id = :companyId
            """)
    Optional<BusinessPack> findByIdAndCompanyIdWithCurrentVersion(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId
    );
}
