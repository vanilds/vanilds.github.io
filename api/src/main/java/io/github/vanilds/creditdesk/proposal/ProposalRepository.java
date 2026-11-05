package io.github.vanilds.creditdesk.proposal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProposalRepository extends JpaRepository<Proposal, UUID>, JpaSpecificationExecutor<Proposal> {

    /**
     * Loads each proposal together with its customer in ONE SQL join. Without the entity graph, showing the
     * customer name for a page of 100 proposals would run 1 + 100 queries (the classic N+1 problem).
     */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Proposal> findAll(Specification<Proposal> spec, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Optional<Proposal> findWithCustomerById(UUID id);

    boolean existsByCustomerIdAndStatusIn(UUID customerId, Collection<ProposalStatus> statuses);

    /** Filters for GET /api/proposals. A null value skips the filter. */
    final class Filters {

        private Filters() {
        }

        static Specification<Proposal> createdBetween(Instant fromInclusive, Instant toExclusive) {
            return (root, query, cb) -> cb.and(
                    cb.greaterThanOrEqualTo(root.get("createdAt"), fromInclusive),
                    cb.lessThan(root.get("createdAt"), toExclusive));
        }

        static Specification<Proposal> statusEquals(ProposalStatus status) {
            return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
        }

        static Specification<Proposal> analystEquals(String analystId) {
            return analystId == null || analystId.isBlank() ? null
                    : (root, query, cb) -> cb.equal(root.get("analystId"), analystId);
        }

        static Specification<Proposal> customerEquals(UUID customerId) {
            return customerId == null ? null : (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
        }
    }
}
