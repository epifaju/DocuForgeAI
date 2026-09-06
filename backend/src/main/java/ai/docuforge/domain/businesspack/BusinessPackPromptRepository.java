package ai.docuforge.domain.businesspack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPackPromptRepository extends JpaRepository<BusinessPackPrompt, UUID> {

    List<BusinessPackPrompt> findByBusinessPackVersionId(UUID businessPackVersionId);

    Optional<BusinessPackPrompt> findByBusinessPackVersionIdAndPromptCodeAndPromptVersion(
            UUID businessPackVersionId,
            String promptCode,
            String promptVersion
    );

    void deleteByBusinessPackVersionId(UUID businessPackVersionId);
}
