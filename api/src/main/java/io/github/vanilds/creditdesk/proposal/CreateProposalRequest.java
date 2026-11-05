package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.proposal.ProposalConstraints.Amount;
import io.github.vanilds.creditdesk.proposal.ProposalConstraints.Installments;
import io.github.vanilds.creditdesk.proposal.ProposalConstraints.MonthlyRate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProposalRequest(
        @NotNull
        UUID customerId,

        @NotNull
        ProposalKind kind,

        @Schema(description = "Principal in BRL, 100.00 to 1,000,000.00", example = "15000.00")
        @Amount
        BigDecimal amount,

        @Schema(description = "Monthly installments, 1 to 120", example = "24")
        @Installments
        Integer installments,

        @Schema(description = "Monthly rate as a fraction: 0.0199 = 1.99% a month (max 0.2)", example = "0.0199")
        @MonthlyRate
        BigDecimal monthlyRate
) {
}
