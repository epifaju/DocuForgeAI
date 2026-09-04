package ai.docuforge.domain.batch;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchItemRepository extends JpaRepository<BatchItem, UUID> {
    List<BatchItem> findByBatchJobIdAndStatusOrderByRowNumberAsc(UUID batchJobId, BatchItemStatus status);

    List<BatchItem> findByBatchJobIdOrderByRowNumberAsc(UUID batchJobId);

    long countByBatchJobId(UUID batchJobId);
}
