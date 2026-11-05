package io.github.vanilds.creditdesk.proposal.opinion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProposalOpinionRepository extends JpaRepository<ProposalOpinion, UUID> {

    List<ProposalOpinion> findByProposalIdOrderByReceivedAtDesc(UUID proposalId);
}
