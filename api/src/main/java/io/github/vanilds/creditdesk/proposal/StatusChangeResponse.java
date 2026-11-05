package io.github.vanilds.creditdesk.proposal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record StatusChangeResponse(
        UUID id,
        @Schema(description = "Null for the creation of the proposal")
        ProposalStatus from,
        ProposalStatus to,
        String actorName,
        String reason,
        Instant changedAt
) {

    static StatusChangeResponse from(ProposalStatusChange change) {
        return new StatusChangeResponse(change.getId(), change.getFromStatus(), change.getToStatus(),
                change.getActorName(), change.getReason(), change.getChangedAt());
    }
}
