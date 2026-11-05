package io.github.vanilds.creditdesk.proposal;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a proposal is in the workflow. The allowed moves between statuses are in {@link ProposalAction}.
 */
public enum ProposalStatus {
    DRAFT, SUBMITTED, IN_ANALYSIS, APPROVED, REJECTED, CANCELLED;

    /** Proposals still being worked on. A customer with any of these cannot be deleted. */
    public static final Set<ProposalStatus> OPEN = EnumSet.of(DRAFT, SUBMITTED, IN_ANALYSIS);
}
