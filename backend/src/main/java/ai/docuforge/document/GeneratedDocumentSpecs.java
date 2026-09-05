package ai.docuforge.document;

import ai.docuforge.domain.document.DocumentStatus;
import ai.docuforge.domain.document.GeneratedDocument;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.jpa.domain.Specification;

final class GeneratedDocumentSpecs {

    private GeneratedDocumentSpecs() {
    }

    static Specification<GeneratedDocument> filtered(
            UUID companyId,
            DocumentStatus status,
            UUID templateId,
            UUID createdBy,
            Instant createdFrom,
            Instant createdTo,
            String q
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (templateId != null) {
                predicates.add(cb.equal(root.get("template").get("id"), templateId));
            }
            if (createdBy != null) {
                predicates.add(cb.equal(root.get("createdBy"), createdBy));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            if (q != null && !q.isBlank()) {
                String pattern = likePattern(q);
                // Cast jsonb → text for PostgreSQL lower()/LIKE over form field values.
                Expression<String> snapshotAsText = cb.lower(
                        ((JpaExpression<?>) root.get("dataSnapshot")).cast(String.class)
                );
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reference")), pattern, '\\'),
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(snapshotAsText, pattern, '\\')
                ));
            }

            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("template");
                root.fetch("templateVersion");
                query.distinct(true);
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Escape LIKE wildcards and wrap with {@code %…%} (case-insensitive match via lower()). */
    static String likePattern(String raw) {
        String escaped = raw.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .toLowerCase(Locale.ROOT);
        return "%" + escaped + "%";
    }
}
