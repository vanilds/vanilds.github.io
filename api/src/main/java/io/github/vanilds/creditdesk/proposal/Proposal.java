package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.customer.Customer;
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

    @Column(name = "analyst_id")
    private String analystId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: Phase 2 exposes it as ETag / If-Match so two analysts cannot overwrite each other. */
    @Version
    private long version;

    protected Proposal() {
        // for JPA
    }

    /** Every proposal starts as a DRAFT. */
    public static Proposal draft(Customer customer, ProposalKind kind, BigDecimal amount, int installments,
                                 BigDecimal monthlyRate, Instant now) {
        var proposal = new Proposal();
        proposal.id = UUID.randomUUID();
        proposal.customer = customer;
        proposal.kind = kind;
        proposal.amount = amount;
        proposal.installments = installments;
        proposal.monthlyRate = monthlyRate;
        proposal.status = ProposalStatus.DRAFT;
        proposal.createdAt = now;
        proposal.updatedAt = now;
        return proposal;
    }

    /** The rule lives in the entity, not in the controller: no code path can edit a submitted proposal. */
    public void update(ProposalKind kind, BigDecimal amount, int installments, BigDecimal monthlyRate, Instant now) {
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
