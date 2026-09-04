package ai.docuforge.domain.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateVariableRepository extends JpaRepository<TemplateVariable, UUID> {

    List<TemplateVariable> findByTemplateVersionIdOrderByDisplayOrderAsc(UUID templateVersionId);

    Optional<TemplateVariable> findByTemplateVersionIdAndVariableKey(UUID templateVersionId, String variableKey);

    long countByTemplateVersionId(UUID templateVersionId);
}