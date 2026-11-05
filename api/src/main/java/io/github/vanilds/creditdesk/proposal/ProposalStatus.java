package io.github.vanilds.creditdesk.proposal;

import java.util.EnumSet;
import java.util.Set;

/**
 * DRAFT → SUBMITTED → IN_ANALYSIS → APPROVED | REJECTED, with IN_ANALYSIS → DRAFT (returned) and
 * DRAFT → CANCELLED. Phase 1 only creates and edits drafts; the transitions arrive in Phase 2 with roles.
 */
public enum ProposalStatus {
    DRAFT, SUBMITTED, IN_ANALYSIS, APPROVED, REJECTED, CANCELLED;

    /** Proposals still being worked on. A customer with any of these cannot be deleted. */
    public static final Set<ProposalStatus> OPEN = EnumSet.of(DRAFT, SUBMITTED, IN_ANALYSIS);
}
