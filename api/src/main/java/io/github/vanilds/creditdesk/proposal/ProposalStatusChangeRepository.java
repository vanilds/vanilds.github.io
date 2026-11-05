package io.github.vanilds.creditdesk.proposal;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Append-only, like the audit log: save and read, nothing else. */
public interface ProposalStatusChangeRepository extends Repository<ProposalStatusChange, UUID> {

    ProposalStatusChange save(ProposalStatusChange change);

    List<ProposalStatusChange> findByProposalIdOrderByChangedAtAscIdAsc(UUID proposalId);
}
