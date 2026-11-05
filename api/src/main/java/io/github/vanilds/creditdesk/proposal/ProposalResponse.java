package io.github.vanilds.creditdesk.proposal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProposalResponse(
        UUID id,
        UUID customerId,
        String customerName,
        ProposalKind kind,
        BigDecimal amount,
        int installments,
        BigDecimal monthlyRate,
        @Schema(description = "Fixed monthly installment (Tabela Price)", example = "792.17")
        BigDecimal installmentAmount,
        @Schema(description = "installmentAmount × installments", example = "19012.08")
        BigDecimal totalAmount,
        ProposalStatus status,
        String analystId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    static ProposalResponse from(Proposal proposal) {
        BigDecimal installment = proposal.installmentAmount();
        return new ProposalResponse(proposal.getId(), proposal.getCustomer().getId(),
                proposal.getCustomer().getName(), proposal.getKind(), proposal.getAmount(),
                proposal.getInstallments(), proposal.getMonthlyRate(), installment,
                PriceTable.total(installment, proposal.getInstallments()), proposal.getStatus(),
                proposal.getAnalystId(), proposal.getCreatedAt(), proposal.getUpdatedAt(), proposal.getVersion());
    }
}
