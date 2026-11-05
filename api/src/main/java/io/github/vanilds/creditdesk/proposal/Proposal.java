package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.customer.Customer;
import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.security.Role;
import io.github.vanilds.creditdesk.shared.web.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Proposal {

    @Id
    private UUID id;

    // LAZY: listing proposals must not load customers one by one (the repository fetches them in the same query)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalKind kind;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private int installments;

    @Column(name = "monthly_rate", nullable = false)
    private BigDecimal monthlyRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    /** Owner: Keycloak id of the analyst who created it. Only the owner edits, submits or cancels. */
    @Column(name = "analyst_id")
    private String analystId;

    @Column(name = "analyst_name")
    private String analystName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking, exposed as ETag / If-Match so two people cannot overwrite each other. */
    @Version
    private long version;

    protected Proposal() {
        // for JPA
    }

    /** Every proposal starts as a DRAFT owned by whoever creates it. */
    public static Proposal draft(Customer customer, ProposalKind kind, BigDecimal amount, int installments,
                                 BigDecimal monthlyRate, Actor author, Instant now) {
        var proposal = new Proposal();
        proposal.id = UUID.randomUUID();
        proposal.customer = customer;
        proposal.kind = kind;
        proposal.amount = amount;
        proposal.installments = installments;
        proposal.monthlyRate = monthlyRate;
        proposal.status = ProposalStatus.DRAFT;
        proposal.analystId = author.id();
        proposal.analystName = author.username();
        proposal.createdAt = now;
        proposal.updatedAt = now;
        return proposal;
    }

    /** The rule lives in the entity, not in the controller: no code path can edit a submitted proposal. */
    public void update(ProposalKind kind, BigDecimal amount, int installments, BigDecimal monthlyRate, Actor actor,
                       Instant now) {
        requireOwner(actor);
        if (status != ProposalStatus.DRAFT) {
            throw ApiException.unprocessable("proposal-not-editable", "Proposal cannot be edited",
                    "Only DRAFT proposals can be edited; this one is %s.".formatted(status));
        }
        this.kind = kind;
        this.amount = amount;
        this.installments = installments;
        this.monthlyRate = monthlyRate;
        this.updatedAt = now;
    }

    /**
     * Moves the proposal along the workflow. All the rules are checked here, in this order, so the error is the
     * most useful one: who may do it (403), whether the state allows it (422), whether a reason is needed (400).
     *
     * @return the status before the move, for the history and the audit log
     */
    public ProposalStatus apply(ProposalAction action, Actor actor, String reason, Instant now) {
        switch (action.performer) {
            case OWNER -> requireOwner(actor);
            case DECIDER -> requireDecider(actor);
        }
        if (status != action.from) {
            throw ApiException.unprocessable("invalid-transition", "Action not allowed in this status",
                    "Cannot %s a proposal that is %s; it must be %s.".formatted(action.path(), status, action.from));
        }
        if (action.reasonRequired && (reason == null || reason.isBlank())) {
            throw ApiException.badRequest("reason-required", "Reason required",
                    "A reason is required to %s a proposal; the analyst will read it.".formatted(action.path()));
        }
        ProposalStatus previous = status;
        status = action.to;
        updatedAt = now;
        return previous;
    }

    /**
     * The AI worker's opinion is ready: the same move as {@link ProposalAction#START_ANALYSIS}, without the person
     * checks. Four-eyes is about who <i>decides</i>, and deciding stays with a manager.
     *
     * @return false when the proposal is no longer waiting at this version (a manager already picked it up, or it
     * was returned and submitted again): the opinion is kept, the status is left alone
     */
    public boolean startAnalysisAutomatically(long submittedVersion, Instant now) {
        if (status != ProposalStatus.SUBMITTED || version != submittedVersion) {
            return false;
        }
        status = ProposalStatus.IN_ANALYSIS;
        updatedAt = now;
        return true;
    }

    /** Documents back the proposal as written, so they can only be added while the owner can still edit it. */
    public void requireCanAttachDocuments(Actor actor) {
        requireOwner(actor);
        if (status != ProposalStatus.DRAFT) {
            throw ApiException.unprocessable("proposal-not-editable", "Proposal cannot be changed",
                    "Documents can only be added while the proposal is a DRAFT; this one is %s.".formatted(status));
        }
    }

    private void requireOwner(Actor actor) {
        if (!actor.id().equals(analystId)) {
            throw ApiException.forbidden("not-proposal-owner", "Not your proposal",
                    "Only the analyst who created this proposal can change it.");
        }
    }

    /**
     * Four-eyes principle (segregation of duties, required by bank regulators): whoever writes a credit proposal
     * cannot be the one who approves it — not even a manager approving their own.
     */
    private void requireDecider(Actor actor) {
        if (!actor.has(Role.MANAGER)) {
            throw ApiException.forbidden("forbidden", "Access denied", "Only a manager can decide proposals.");
        }
        if (actor.id().equals(analystId)) {
            throw ApiException.forbidden("four-eyes", "Four-eyes principle",
                    "You created this proposal, so someone else must decide it.");
        }
    }

    public BigDecimal installmentAmount() {
        return PriceTable.installment(amount, monthlyRate, installments);
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public ProposalKind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getInstallments() {
        return installments;
    }

    public BigDecimal getMonthlyRate() {
        return monthlyRate;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public String getAnalystId() {
        return analystId;
    }

    public String getAnalystName() {
        return analystName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
