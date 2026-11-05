package io.github.vanilds.creditdesk.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
public class Customer {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CustomerType type;

    /** Normalized CPF/CNPJ. Immutable: a different document is a different customer. */
    @Column(nullable = false, updatable = false)
    private String document;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "monthly_revenue", nullable = false)
    private BigDecimal monthlyRevenue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    protected Customer() {
        // for JPA
    }

    public Customer(CustomerType type, String document, String name, String email, BigDecimal monthlyRevenue,
                    Instant now) {
        this.id = UUID.randomUUID();
        this.type = Objects.requireNonNull(type);
        this.document = Objects.requireNonNull(document);
        this.name = name;
        this.email = email;
        this.monthlyRevenue = monthlyRevenue;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String email, BigDecimal monthlyRevenue, Instant now) {
        this.name = name;
        this.email = email;
        this.monthlyRevenue = monthlyRevenue;
        this.updatedAt = now;
    }

    /** Soft delete: the row stays for history (proposals keep pointing to it) and for audit. */
    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String displayDocument() {
        return type.displayDocument(document);
    }

    public UUID getId() {
        return id;
    }

    public CustomerType getType() {
        return type;
    }

    public String getDocument() {
        return document;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public BigDecimal getMonthlyRevenue() {
        return monthlyRevenue;
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
