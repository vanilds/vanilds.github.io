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

    /*
     * The CPF/CNPJ is never stored in clear (see CustomerDocuments). Immutable: a different document is a
     * different customer.
     */

    /** HMAC of the normalized document: equality search and the unique index use this. */
    @Column(name = "document_hash", nullable = false, updatable = false)
    private String documentHash;

    /** AES-GCM ciphertext; decrypted only when an auditor reveals it. */
    @Column(name = "document_encrypted", nullable = false, updatable = false)
    private byte[] documentEncrypted;

    /** Masked CPF / formatted CNPJ, computed once at creation so listing a page never decrypts anything. */
    @Column(name = "document_display", nullable = false, updatable = false)
    private String documentDisplay;

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

    public Customer(CustomerType type, CustomerDocuments.Protected document, String name, String email,
                    BigDecimal monthlyRevenue, Instant now) {
        this.id = UUID.randomUUID();
        this.type = Objects.requireNonNull(type);
        this.documentHash = document.hash();
        this.documentEncrypted = document.encrypted();
        this.documentDisplay = document.display();
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
        return documentDisplay;
    }

    byte[] getDocumentEncrypted() {
        return documentEncrypted;
    }

    public UUID getId() {
        return id;
    }

    public CustomerType getType() {
        return type;
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
