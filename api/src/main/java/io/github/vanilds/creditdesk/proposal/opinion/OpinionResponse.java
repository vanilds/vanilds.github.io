package io.github.vanilds.creditdesk.proposal.opinion;

import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.Recommendation;
import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.RiskFlag;
import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.RiskLevel;
import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.SuggestedPlan;
import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.Summary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "AI credit opinion. Advisory: it supports the manager's decision, it never makes it.")
public record OpinionResponse(
        UUID id,
        @Schema(description = "The submitted version it was drafted for")
        long proposalVersion,
        @Schema(description = "true for the opinion on the latest submission; false once the proposal was returned "
                + "or submitted again after it")
        boolean current,
        Recommendation recommendation,
        RiskLevel riskLevel,
        Summary summary,
        List<RiskFlag> riskFlags,
        @Schema(description = "Figures the rules used, e.g. installmentToIncome = 0.31")
        Map<String, Object> indicators,
        SuggestedPlan suggestedPlan,
        @Schema(description = "Model that wrote the summary, or rules-template when no LLM is configured",
                example = "rules-template-v1")
        String model,
        Instant generatedAt,
        Instant receivedAt
) {

    static OpinionResponse from(ProposalOpinion opinion, boolean current) {
        return new OpinionResponse(opinion.getId(), opinion.getProposalVersion(), current, opinion.getRecommendation(),
                opinion.getRiskLevel(), new Summary(opinion.getSummaryEn(), opinion.getSummaryPt()),
                opinion.getRiskFlags(), opinion.getIndicators(), opinion.getSuggestedPlan(), opinion.getModel(),
                opinion.getGeneratedAt(), opinion.getReceivedAt());
    }
}
