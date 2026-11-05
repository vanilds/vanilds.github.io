package io.github.vanilds.creditdesk.proposal.opinion;

import io.github.vanilds.creditdesk.audit.AuditAction;
import io.github.vanilds.creditdesk.audit.AuditService;
import io.github.vanilds.creditdesk.audit.Changes;
import io.github.vanilds.creditdesk.messaging.Inbox;
import io.github.vanilds.creditdesk.proposal.Proposal;
import io.github.vanilds.creditdesk.proposal.ProposalService;
import io.github.vanilds.creditdesk.proposal.ProposalStatus;
import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OpinionService {

    static final String CONSUMER = "api.opinion-drafted";
    private static final Logger log = LoggerFactory.getLogger(OpinionService.class);

    private final ProposalOpinionRepository opinions;
    private final ProposalService proposals;
    private final Inbox inbox;
    private final AuditService audit;
    private final Clock clock;

    public OpinionService(ProposalOpinionRepository opinions, ProposalService proposals, Inbox inbox,
                          AuditService audit, Clock clock) {
        this.opinions = opinions;
        this.proposals = proposals;
        this.inbox = inbox;
        this.audit = audit;
        this.clock = clock;
    }

    /** Thrown for a message that no retry can fix; it goes straight to the dead-letter queue. */
    static class UnprocessableOpinion extends RuntimeException {
        UnprocessableOpinion(String message) {
            super(message);
        }
    }

    /**
     * One transaction: inbox row, opinion, audit line and (usually) the move to IN_ANALYSIS. A duplicate delivery
     * stops at the inbox and changes nothing.
     */
    @Transactional
    public void record(UUID opinionId, OpinionDrafted event) {
        if (!inbox.firstDelivery(CONSUMER, opinionId)) {
            log.info("Opinion {} already recorded, duplicate delivery ignored", opinionId);
            return;
        }
        Proposal proposal;
        try {
            proposal = proposals.require(event.proposalId());
        } catch (ApiException notFound) {
            throw new UnprocessableOpinion("Unknown proposal " + event.proposalId());
        }
        if (event.proposalVersion() > proposal.getVersion()) {
            throw new UnprocessableOpinion("Opinion for version %d, but proposal %s is at %d"
                    .formatted(event.proposalVersion(), proposal.getId(), proposal.getVersion()));
        }
        opinions.save(new ProposalOpinion(opinionId, event, clock.instant()));
        Actor worker = Actor.system("ai-worker");
        audit.record(worker, AuditAction.PROPOSAL_OPINION_RECORDED, "PROPOSAL", proposal.getId(), new Changes()
                .created("opinionId", opinionId)
                .created("recommendation", event.recommendation())
                .created("riskLevel", event.riskLevel())
                .created("model", event.model()), null);
        boolean moved = proposals.startAnalysisAfterOpinion(proposal.getId(), event.proposalVersion());
        log.info("Opinion {} recorded for proposal {} ({}, {}); {}", opinionId, proposal.getId(),
                event.recommendation(), event.riskLevel(),
                moved ? "proposal moved to IN_ANALYSIS" : "status left as " + proposal.getStatus());
    }

    /** Newest first. Only the newest one on the latest submission is marked {@code current}. */
    public List<OpinionResponse> list(UUID proposalId) {
        Proposal proposal = proposals.require(proposalId);
        Instant lastSubmittedAt = proposals.lastSubmittedAt(proposalId);
        boolean waitingOrDecided = proposal.getStatus() != ProposalStatus.DRAFT
                && proposal.getStatus() != ProposalStatus.CANCELLED;
        List<OpinionResponse> result = new ArrayList<>();
        for (ProposalOpinion opinion : opinions.findByProposalIdOrderByReceivedAtDesc(proposalId)) {
            boolean current = result.isEmpty() && waitingOrDecided && lastSubmittedAt != null
                    && !opinion.getReceivedAt().isBefore(lastSubmittedAt);
            result.add(OpinionResponse.from(opinion, current));
        }
        return result;
    }
}
