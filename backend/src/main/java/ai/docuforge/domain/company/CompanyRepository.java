package ai.docuforge.domain.company;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByIdentifier(String identifier);
}