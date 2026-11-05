package io.github.vanilds.creditdesk.proposal.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposalDocumentRepository extends JpaRepository<ProposalDocument, UUID> {

    List<ProposalDocument> findByProposalIdAndStatusOrderByCreatedAt(UUID proposalId, DocumentStatus status);

    Optional<ProposalDocument> findByIdAndProposalId(UUID id, UUID proposalId);

    long countByProposalId(UUID proposalId);

    /** For the proposal.submitted event: which kinds of proof are attached (no names, no files). */
    @Query("""
            select distinct d.category from ProposalDocument d
            where d.proposalId = :proposalId and d.status = io.github.vanilds.creditdesk.proposal.document.DocumentStatus.AVAILABLE
            order by d.category""")
    List<DocumentCategory> availableCategories(UUID proposalId);
}
