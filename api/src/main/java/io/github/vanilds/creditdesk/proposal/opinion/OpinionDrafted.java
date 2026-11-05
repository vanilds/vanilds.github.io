package io.github.vanilds.creditdesk.proposal.opinion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event {@code opinion.drafted}, published by the AI worker. The message id (AMQP {@code message_id}) is the
 * opinion id; the worker derives it from the submission event, so a re-processed submission yields the same id.
 *
 * <p>Validated like a request body: another service wrote it, so it is input, not trusted data.
 */
public record OpinionDrafted(
        @NotNull UUID proposalId,
        @PositiveOrZero long proposalVersion,
        @NotNull Recommendation recommendation,
        @NotNull RiskLevel riskLevel,
        @NotNull @Valid Summary summary,
        @NotNull @Size(max = 20) List<@Valid RiskFlag> riskFlags,
        @NotNull Map<String, Object> indicators,
        @Valid SuggestedPlan suggestedPlan,
        @NotBlank @Size(max = 100) String model,
        @NotNull Instant generatedAt
) {

    public enum Recommendation { FAVORABLE, CAUTION, UNFAVORABLE }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public record Summary(@NotBlank @Size(max = 4000) String en, @NotBlank @Size(max = 4000) String pt) {
    }

    public record RiskFlag(
            @Schema(example = "HIGH_INSTALLMENT_TO_INCOME") @NotBlank @Size(max = 60) String code,
            @NotNull RiskLevel severity,
            @Schema(description = "English explanation; the front translates by code")
            @NotBlank @Size(max = 500) String message) {
    }

    /** A plan that fits the income, when the requested one does not. */
    public record SuggestedPlan(
            @Positive int installments,
            @NotNull @DecimalMin("0.01") BigDecimal installmentAmount,
            @NotNull BigDecimal installmentToIncome) {
    }
}
