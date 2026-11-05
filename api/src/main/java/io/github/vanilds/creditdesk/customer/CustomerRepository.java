package io.github.vanilds.creditdesk.customer;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    boolean existsByDocumentHashAndDeletedAtIsNull(String documentHash);

    Optional<Customer> findByIdAndDeletedAtIsNull(UUID id);

    /** Filters for GET /api/customers. Each one is skipped when its value is null. */
    final class Filters {

        private Filters() {
        }

        static Specification<Customer> active() {
            return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        }

        /** Exact match on the document's HMAC — the only way to search an encrypted column. */
        static Specification<Customer> documentHashEquals(String documentHash) {
            return documentHash == null ? null : (root, query, cb) -> cb.equal(root.get("documentHash"), documentHash);
        }

        static Specification<Customer> nameContains(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            String pattern = "%" + escapeLike(name.trim().toLowerCase()) + "%";
            return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
        }

        // "50%" must search for the text "50%", not "50 followed by anything"
        private static String escapeLike(String value) {
            return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
    }
}
