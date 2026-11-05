package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.audit.AuditAction;

import static io.github.vanilds.creditdesk.proposal.ProposalStatus.APPROVED;
import static io.github.vanilds.creditdesk.proposal.ProposalStatus.CANCELLED;
import static io.github.vanilds.creditdesk.proposal.ProposalStatus.DRAFT;
import static io.github.vanilds.creditdesk.proposal.ProposalStatus.IN_ANALYSIS;
import static io.github.vanilds.creditdesk.proposal.ProposalStatus.REJECTED;
import static io.github.vanilds.creditdesk.proposal.ProposalStatus.SUBMITTED;

/**
 * The workflow as data: every allowed move is one line here, so the state machine can be read (and reviewed by
 * the business) in one place. Anything not listed is refused with 422.
 *
 * <pre>
 *            submit             start-analysis           approve
 *   DRAFT ───────────▶ SUBMITTED ─────────────▶ IN_ANALYSIS ───────▶ APPROVED
 *     │  ▲                                        │   │
 *     │  └──────────────── return (reason) ───────┘   └── reject (reason) ──▶ REJECTED
 *     └── cancel ──▶ CANCELLED
 * </pre>
 *
 * In Phase 4 the AI worker moves SUBMITTED → IN_ANALYSIS when its credit opinion is ready. start-analysis stays
 * as the manual path a manager uses when the worker is down: the business never depends on the AI to move on.
 */
public enum ProposalAction {

    SUBMIT(DRAFT, SUBMITTED, Performer.OWNER, false, AuditAction.PROPOSAL_SUBMITTED),
    CANCEL(DRAFT, CANCELLED, Performer.OWNER, false, AuditAction.PROPOSAL_CANCELLED),
    START_ANALYSIS(SUBMITTED, IN_ANALYSIS, Performer.DECIDER, false, AuditAction.PROPOSAL_ANALYSIS_STARTED),
    APPROVE(IN_ANALYSIS, APPROVED, Performer.DECIDER, false, AuditAction.PROPOSAL_APPROVED),
    REJECT(IN_ANALYSIS, REJECTED, Performer.DECIDER, true, AuditAction.PROPOSAL_REJECTED),
    RETURN(IN_ANALYSIS, DRAFT, Performer.DECIDER, true, AuditAction.PROPOSAL_RETURNED);

    /** Who may perform the action, beyond the role checked at the endpoint. */
    enum Performer {
        /** The analyst who created the proposal. */
        OWNER,
        /** A manager who is NOT the proposal's author (four-eyes principle). */
        DECIDER
    }

    final ProposalStatus from;
    final ProposalStatus to;
    final Performer performer;
    final boolean reasonRequired;
    final AuditAction auditAction;

    ProposalAction(ProposalStatus from, ProposalStatus to, Performer performer, boolean reasonRequired,
                   AuditAction auditAction) {
        this.from = from;
        this.to = to;
        this.performer = performer;
        this.reasonRequired = reasonRequired;
        this.auditAction = auditAction;
    }

    /** URL segment: START_ANALYSIS → "start-analysis". */
    public String path() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
