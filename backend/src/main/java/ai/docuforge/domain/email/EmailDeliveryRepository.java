package ai.docuforge.domain.email;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {

    @Modifying
    @Query("delete from EmailDelivery e where e.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);
}
