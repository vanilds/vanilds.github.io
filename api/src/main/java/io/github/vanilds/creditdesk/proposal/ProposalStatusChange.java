package io.github.vanilds.creditdesk.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * The proposal's timeline, shown to the analyst ("returned by marcos: missing income proof").
 *
 * <p>Why a table besides the audit log? Different readers, different rules: the analyst needs the history of
 * <i>their</i> proposal, but must not read the audit log (which holds everyone's actions and CPF reveals).
 */
@Entity
@Immutable
@Table(name = "proposal_status_history")
public class ProposalStatusChange {

    @Id
    private UUID id;

    @Column(name = "proposal_id", nullable = false)
    private UUID proposalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ProposalStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ProposalStatus toStatus;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ProposalStatusChange() {
        // for JPA
    }

    ProposalStatusChange(UUID proposalId, ProposalStatus fromStatus, ProposalStatus toStatus, String actorId,
                         String actorName, String reason, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.proposalId = proposalId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.actorName = actorName;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public ProposalStatus getFromStatus() {
        return fromStatus;
    }

    public ProposalStatus getToStatus() {
        return toStatus;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
