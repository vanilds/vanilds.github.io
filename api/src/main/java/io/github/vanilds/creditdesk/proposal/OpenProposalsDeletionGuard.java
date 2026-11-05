package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.customer.CustomerDeletionGuard;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** A customer with a proposal still in progress (DRAFT, SUBMITTED, IN_ANALYSIS) cannot be deleted. */
@Component
class OpenProposalsDeletionGuard implements CustomerDeletionGuard {

    private final ProposalRepository proposals;

    OpenProposalsDeletionGuard(ProposalRepository proposals) {
        this.proposals = proposals;
    }

    @Override
    public String blockingReason(UUID customerId) {
        return proposals.existsByCustomerIdAndStatusIn(customerId, ProposalStatus.OPEN)
                ? "The customer has open proposals. Finish or cancel them first."
                : null;
    }
}
