package io.github.vanilds.creditdesk.audit;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately NOT a JpaRepository: no {@code delete}, no {@code saveAll} of edited rows. The only write is
 * {@link #save} of a new entry — the code cannot even express "change the past".
 */
public interface AuditRepository extends Repository<AuditEntry, UUID>, JpaSpecificationExecutor<AuditEntry> {

    AuditEntry save(AuditEntry entry);

    final class Filters {

        private Filters() {
        }

        static Specification<AuditEntry> occurredBetween(Instant fromInclusive, Instant toExclusive) {
            return (root, query, cb) -> cb.and(
                    cb.greaterThanOrEqualTo(root.get("occurredAt"), fromInclusive),
                    cb.lessThan(root.get("occurredAt"), toExclusive));
        }

        static Specification<AuditEntry> equalTo(String attribute, Object value) {
            return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
        }
    }
}
