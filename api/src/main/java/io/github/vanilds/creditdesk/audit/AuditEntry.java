package io.github.vanilds.creditdesk.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One line of the audit trail. {@code @Immutable}: Hibernate never issues an UPDATE for it, and the database
 * trigger in V3 rejects UPDATE and DELETE from anyone (see the migration) — append-only at both levels.
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** {"field": {"from": …, "to": …}} — personal data already masked before it gets here. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> changes;

    private String reason;

    @Column(name = "correlation_id")
    private String correlationId;

    protected AuditEntry() {
        // for JPA
    }

    AuditEntry(Instant occurredAt, String actorId, String actorName, AuditAction action, String entityType,
               UUID entityId, Map<String, Object> changes, String reason, String correlationId) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.changes = changes == null || changes.isEmpty() ? null : changes;
        this.reason = reason;
        this.correlationId = correlationId;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Map<String, Object> getChanges() {
        return changes;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
