package io.github.vanilds.creditdesk.proposal.opinion;

import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.Recommendation;
import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.RiskFlag;
import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.RiskLevel;
import io.github.vanilds.creditdesk.proposal.opinion.OpinionDrafted.SuggestedPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An AI credit opinion, as received. Never edited: a new submission gets a new opinion. */
@Entity
@Immutable
@Table(name = "proposal_opinion")
public class ProposalOpinion implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "proposal_id", nullable = false)
    private UUID proposalId;

    @Column(name = "proposal_version", nullable = false)
    private long proposalVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recommendation recommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "summary_en", nullable = false)
    private String summaryEn;

    @Column(name = "summary_pt", nullable = false)
    private String summaryPt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_flags", columnDefinition = "jsonb", nullable = false)
    private List<RiskFlag> riskFlags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> indicators;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_plan", columnDefinition = "jsonb")
    private SuggestedPlan suggestedPlan;

    @Column(nullable = false)
    private String model;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ProposalOpinion() {
        // for JPA
    }

    ProposalOpinion(UUID id, OpinionDrafted event, Instant receivedAt) {
        this.id = id;
        this.proposalId = event.proposalId();
        this.proposalVersion = event.proposalVersion();
        this.recommendation = event.recommendation();
        this.riskLevel = event.riskLevel();
        this.summaryEn = event.summary().en();
        this.summaryPt = event.summary().pt();
        this.riskFlags = List.copyOf(event.riskFlags());
        this.indicators = event.indicators();
        this.suggestedPlan = event.suggestedPlan();
        this.model = event.model();
        this.generatedAt = event.generatedAt();
        this.receivedAt = receivedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    /**
     * Always new: an opinion is inserted once and never updated. Without this, save() with a given id would first
     * SELECT to decide between insert and update, and a duplicate id would be silently merged instead of failing.
     */
    @Override
    public boolean isNew() {
        return true;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public long getProposalVersion() {
        return proposalVersion;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getSummaryEn() {
        return summaryEn;
    }

    public String getSummaryPt() {
        return summaryPt;
    }

    public List<RiskFlag> getRiskFlags() {
        return riskFlags;
    }

    public Map<String, Object> getIndicators() {
        return indicators;
    }

    public SuggestedPlan getSuggestedPlan() {
        return suggestedPlan;
    }

    public String getModel() {
        return model;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
