package io.github.vanilds.creditdesk.audit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        Instant occurredAt,
        @Schema(description = "Keycloak user id (token subject)")
        String actorId,
        @Schema(description = "Username at the time of the action", example = "marcos")
        String actorName,
        AuditAction action,
        @Schema(example = "PROPOSAL")
        String entityType,
        UUID entityId,
        @Schema(description = "Changed fields as {\"field\": {\"from\": …, \"to\": …}}; personal data masked")
        Map<String, Object> changes,
        String reason,
        String correlationId
) {

    static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(entry.getId(), entry.getOccurredAt(), entry.getActorId(), entry.getActorName(),
                entry.getAction(), entry.getEntityType(), entry.getEntityId(), entry.getChanges(), entry.getReason(),
                entry.getCorrelationId());
    }
}
