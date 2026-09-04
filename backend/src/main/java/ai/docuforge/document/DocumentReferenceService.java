package ai.docuforge.document;

import ai.docuforge.domain.document.GeneratedDocumentRepository;
import java.time.Year;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * PRD §29 — document references like DOC-YYYY-######.
 */
@Service
public class DocumentReferenceService {

    private final GeneratedDocumentRepository generatedDocumentRepository;

    public DocumentReferenceService(GeneratedDocumentRepository generatedDocumentRepository) {
        this.generatedDocumentRepository = generatedDocumentRepository;
    }

    public String allocate(UUID companyId) {
        int year = Year.now().getValue();
        for (int attempt = 0; attempt < 25; attempt++) {
            long seq = generatedDocumentRepository.countByCompanyId(companyId)
                    + 1
                    + ThreadLocalRandom.current().nextInt(0, 3);
            String candidate = "DOC-%d-%06d".formatted(year, Math.min(seq, 999_999L));
            if (generatedDocumentRepository.findByCompanyIdAndReference(companyId, candidate).isEmpty()) {
                return candidate;
            }
        }
        return "DOC-%d-%06d".formatted(year, ThreadLocalRandom.current().nextInt(1, 999_999));
    }

    public boolean isUniqueViolation(RuntimeException ex) {
        return ex instanceof DataIntegrityViolationException;
    }
}