package ai.docuforge.domain.email;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {
}
