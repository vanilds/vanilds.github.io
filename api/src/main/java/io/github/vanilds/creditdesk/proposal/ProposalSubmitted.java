package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.customer.CustomerType;
import io.github.vanilds.creditdesk.proposal.document.DocumentCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Event {@code proposal.submitted}: what the AI worker needs to draft an opinion, and nothing more.
 *
 * <p><b>Data minimization (LGPD, art. 6, III):</b> no name, no CPF/CNPJ, no e-mail. The worker (and any LLM
 * behind it) sees amounts, ratios and counts; nothing in the message identifies a person. That also means a
 * leaked message or an LLM provider's log holds nothing personal.
 *
 * <p>It is a contract with another service, written in another language: fields are only ever added, never renamed
 * or removed (ai-worker/src/creditdesk_worker/messages.py mirrors it).
 */
public record ProposalSubmitted(
        UUID proposalId,
        /* The version right after the submit. The opinion refers to it, so a late opinion for an older
           submission never moves a proposal that was returned and submitted again. */
        long proposalVersion,
        ProposalKind kind,
        BigDecimal amount,
        int installments,
        BigDecimal monthlyRate,
        BigDecimal installmentAmount,
        CustomerProfile customer,
        List<DocumentCategory> documents,
        Instant submittedAt
) {

    public record CustomerProfile(
            CustomerType type,
            /* Monthly income (PF) or revenue (PJ) */
            BigDecimal monthlyRevenue,
            LocalDate customerSince,
            long approvedLast12Months,
            long rejectedLast12Months,
            /* Other proposals of the same customer still in progress */
            long otherOpenProposals
    ) {
    }
}
