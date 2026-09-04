package ai.docuforge.domain.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByCompanyIdAndEmail(UUID companyId, String email);

    Page<UserAccount> findByCompanyId(UUID companyId, Pageable pageable);

    @Query("""
            select distinct u from UserAccount u
            left join fetch u.roles
            where u.company.id = :companyId and u.id = :id
            """)
    Optional<UserAccount> findByIdAndCompanyIdWithRoles(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId
    );

    @Query("""
            select distinct u from UserAccount u
            join fetch u.company c
            left join fetch u.roles
            where lower(c.identifier) = lower(:companyIdentifier)
              and lower(u.email) = lower(:email)
            """)
    Optional<UserAccount> findByCompanyIdentifierAndEmail(
            @Param("companyIdentifier") String companyIdentifier,
            @Param("email") String email
    );

    @Query("""
            select distinct u from UserAccount u
            join fetch u.company
            left join fetch u.roles
            where u.id = :id
            """)
    Optional<UserAccount> findByIdWithCompanyAndRoles(@Param("id") UUID id);
}
